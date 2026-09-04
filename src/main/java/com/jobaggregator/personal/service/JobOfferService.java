package com.jobaggregator.personal.service;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.dto.JobStatsDto;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.repository.JobOfferRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;
    private final SpanishGeographyService spanishGeographyService;
    private final TechnologyParserService technologyParserService;

    @Transactional(readOnly = true)
    public Page<JobOfferResponseDto> getOffers(
            JobStatus status,
            String keyword,
            String technology,
            Boolean isRemote,
            String location,
            String country,
            String community,
            String province,
            JobModality modality,
            List<String> studyLevels,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("publishedDate"), Sort.Order.desc("id"))
        );

        Specification<JobOffer> spec = buildSpec(status, keyword, technology, isRemote,
                location, country, community, province, modality, studyLevels);

        return jobOfferRepository.findAll(spec, pageable).map(entity -> {
            JobOfferResponseDto dto = JobOfferResponseDto.fromEntity(entity);
            dto.setJuniorScore(technologyParserService.computeJuniorScore(
                    entity.getTitle(), entity.getFullDescription(),
                    entity.getRequiredTechnologies(), entity.getStudyLevels()));
            return dto;
        });
    }

    private Specification<JobOffer> buildSpec(
            JobStatus status,
            String keyword,
            String technology,
            Boolean isRemote,
            String location,
            String country,
            String community,
            String province,
            JobModality modality,
            List<String> studyLevels
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Keyword search – includes location fields so searching "España", "Madrid" etc. works.
            // We check BOTH the accent-normalized pattern ("%espana%") AND the original lowercase
            // pattern ("%españa%") to handle databases that preserve 'ñ' in LOWER() (H2, Postgres).
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kwNorm = SpanishGeographyService.removeAccents(keyword.trim().toLowerCase());
                String kwRaw  = keyword.trim().toLowerCase(); // preserves 'ñ', accents, etc.

                List<String> patterns = kwNorm.equals(kwRaw)
                        ? List.of("%" + kwNorm + "%")
                        : List.of("%" + kwNorm + "%", "%" + kwRaw + "%");

                List<Predicate> kwPreds = new ArrayList<>();
                for (String pat : patterns) {
                    kwPreds.add(cb.like(cb.lower(root.get("title")), pat));
                    kwPreds.add(cb.like(cb.lower(root.get("companyName")), pat));
                    kwPreds.add(cb.like(cb.lower(root.get("shortDescription")), pat));
                    kwPreds.add(cb.like(cb.function("lower", String.class, root.get("location")), pat));
                    kwPreds.add(cb.like(cb.function("lower", String.class, root.get("country")), pat));
                    kwPreds.add(cb.like(cb.function("lower", String.class, root.get("provinceOrCity")), pat));
                    kwPreds.add(cb.like(cb.function("lower", String.class, root.get("autonomousCommunity")), pat));
                }
                predicates.add(cb.or(kwPreds.toArray(new Predicate[0])));
            }

            // Technology filter
            if (technology != null && !technology.trim().isEmpty()) {
                Join<JobOffer, String> techJoin = root.join("requiredTechnologies", JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(techJoin), technology.trim().toLowerCase()));
            }

            // Study level filter (multi-value)
            if (studyLevels != null && !studyLevels.isEmpty()) {
                Join<JobOffer, String> studyJoin = root.join("studyLevels", JoinType.LEFT);
                List<Predicate> studyOrs = new ArrayList<>();
                for (String sl : studyLevels) {
                    studyOrs.add(cb.equal(cb.upper(studyJoin), sl.toUpperCase()));
                }
                predicates.add(cb.or(studyOrs.toArray(new Predicate[0])));
            }

            // Modality filter
            if (modality != null) {
                predicates.add(cb.equal(root.get("modality"), modality));
            } else if (Boolean.TRUE.equals(isRemote)) {
                predicates.add(cb.equal(root.get("isRemote"), true));
            } else if (Boolean.FALSE.equals(isRemote)) {
                predicates.add(cb.equal(root.get("isRemote"), false));
            }

            // Hierarchical geographic filter (Strict & Cascading)
            List<Predicate> geoPredicates = new ArrayList<>();

            if (province != null && !province.trim().isEmpty()) {
                // Strict province filter
                String provNorm = normalizeForSearch(province);
                List<Predicate> provPreds = new ArrayList<>();
                provPreds.add(cb.equal(cb.lower(root.get("provinceOrCity")), province.trim().toLowerCase()));
                provPreds.add(cb.like(cb.lower(root.get("location")), "%" + provNorm + "%"));
                geoPredicates.add(cb.or(provPreds.toArray(new Predicate[0])));

            } else if (community != null && !community.trim().isEmpty()) {
                // Strict Autonomous Community filter
                String commNorm = normalizeForSearch(community);
                List<Predicate> commPreds = new ArrayList<>();
                commPreds.add(cb.equal(cb.lower(root.get("autonomousCommunity")), community.trim().toLowerCase()));

                List<String> communityProvinces = spanishGeographyService.getSpanishGeographyTree()
                        .getOrDefault(community.trim(), Collections.emptyList());

                for (String prov : communityProvinces) {
                    String provPat = "%" + normalizeForSearch(prov) + "%";
                    commPreds.add(cb.equal(cb.lower(root.get("provinceOrCity")), prov.toLowerCase()));
                    commPreds.add(cb.like(cb.lower(root.get("location")), provPat));
                }
                geoPredicates.add(cb.or(commPreds.toArray(new Predicate[0])));

            } else if (country != null && !country.trim().isEmpty()) {
                String countryNorm = normalizeForSearch(country);

                if ("espana".equals(countryNorm) || "spain".equals(countryNorm) || "españa".equals(countryNorm)) {
                    // === FILTRO ESPAÑA ESTRICTO ===
                    // Solo incluye ofertas donde country es EXPLÍCITAMENTE España,
                    // o donde la ubicación menciona una provincia/ciudad española.
                    // EXCLUYE cualquier oferta con country de otro país conocido,
                    // aunque autonomousCommunity esté relleno (bug anterior).

                    // Bloque 1: country explícitamente asignado como España
                    List<Predicate> explicitSpain = new ArrayList<>();
                    explicitSpain.add(cb.equal(cb.lower(root.get("country")), "españa"));
                    explicitSpain.add(cb.equal(cb.lower(root.get("country")), "spain"));

                    // Bloque 2: location o provinceOrCity contiene referencia española
                    List<Predicate> locationSpain = new ArrayList<>();
                    locationSpain.add(cb.like(cb.lower(root.get("location")), "%spain%"));
                    locationSpain.add(cb.like(cb.lower(root.get("location")), "%españa%"));
                    locationSpain.add(cb.like(cb.lower(root.get("location")), "%espana%"));

                    // Añadir provincias y ciudades españolas
                    for (List<String> provinces : spanishGeographyService.getSpanishGeographyTree().values()) {
                        for (String prov : provinces) {
                            String p = "%" + normalizeForSearch(prov) + "%";
                            locationSpain.add(cb.like(cb.lower(root.get("location")), p));
                            locationSpain.add(cb.like(cb.lower(root.get("provinceOrCity")), p));
                        }
                    }

                    // Predicado positivo: (country = España) O (location menciona España)
                    Predicate isSpain = cb.or(
                            cb.or(explicitSpain.toArray(new Predicate[0])),
                            cb.or(locationSpain.toArray(new Predicate[0]))
                    );

                    // Predicado negativo (NULL-safe): excluir SOLO si country está explícitamente
                    // establecido como un país extranjero conocido.
                    // cb.notEqual sobre campo NULL → UNKNOWN en SQL → se rechazaba toda oferta sin country.
                    // Solución: (country IS NULL) OR (country IN spain_values) OR (country NOT IN foreign_list)
                    List<String> foreignCountries = List.of(
                            "reino unido", "uk", "united kingdom",
                            "alemania", "germany", "deutschland",
                            "francia", "france",
                            "estados unidos", "usa", "united states",
                            "polonia", "poland",
                            "italia", "italy",
                            "paises bajos", "netherlands", "holanda",
                            "portugal", "brasil", "brazil",
                            "argentina", "mexico",
                            "colombia", "chile", "peru",
                            "rumania", "romania",
                            "irlanda", "ireland",
                            "suecia", "sweden",
                            "noruega", "norway",
                            "dinamarca", "denmark"
                    );

                    // NULL-safe: if country is NULL → we allow (might be Spain based on isSpain check)
                    // if country is set → it must NOT be in the foreign list
                    Predicate countryIsNull   = cb.isNull(root.get("country"));
                    Predicate countryIsSpain  = cb.or(
                            cb.equal(cb.lower(root.get("country")), "españa"),
                            cb.equal(cb.lower(root.get("country")), "spain")
                    );
                    @SuppressWarnings("unchecked")
                    Expression<String> lowerCountry = (Expression<String>) cb.lower(root.get("country"));
                    Predicate countryNotForeign = cb.not(lowerCountry.in(foreignCountries));
                    Predicate notForeign = cb.or(countryIsNull, countryIsSpain, countryNotForeign);

                    geoPredicates.add(cb.and(isSpain, notForeign));

                } else if ("worldwide".equals(countryNorm) || "internacional".equals(countryNorm) || "global".equals(countryNorm)) {
                    geoPredicates.add(cb.or(
                            cb.like(cb.lower(root.get("country")), "%worldwide%"),
                            cb.like(cb.lower(root.get("country")), "%global%"),
                            cb.like(cb.lower(root.get("country")), "%internacional%"),
                            cb.like(cb.lower(root.get("location")), "%worldwide%"),
                            cb.like(cb.lower(root.get("location")), "%global%")
                    ));
                } else {
                    // Other specific country (e.g. Alemania, Reino Unido, Francia)
                    geoPredicates.add(cb.or(
                            cb.like(cb.lower(root.get("country")), "%" + countryNorm + "%"),
                            cb.like(cb.lower(root.get("location")), "%" + countryNorm + "%")
                    ));
                }

            } else if (location != null && !location.trim().isEmpty()) {
                // Generic location text fallback – check both normalized and accented patterns
                String locNorm = normalizeForSearch(location);
                String locRaw  = location.trim().toLowerCase();
                List<String> locPats = locNorm.equals(locRaw)
                        ? List.of("%" + locNorm + "%")
                        : List.of("%" + locNorm + "%", "%" + locRaw + "%");
                List<Predicate> locPreds = new ArrayList<>();
                for (String pat : locPats) {
                    locPreds.add(cb.like(cb.lower(root.get("location")), pat));
                    locPreds.add(cb.like(cb.lower(root.get("country")), pat));
                    locPreds.add(cb.like(cb.lower(root.get("autonomousCommunity")), pat));
                    locPreds.add(cb.like(cb.lower(root.get("provinceOrCity")), pat));
                }
                geoPredicates.add(cb.or(locPreds.toArray(new Predicate[0])));
            }


            if (!geoPredicates.isEmpty()) {
                predicates.add(cb.and(geoPredicates.toArray(new Predicate[0])));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeForSearch(String input) {
        return SpanishGeographyService.removeAccents(input.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public JobOfferResponseDto getOfferById(Long id) {
        JobOffer offer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la oferta con id: " + id));
        JobOfferResponseDto dto = JobOfferResponseDto.fromEntity(offer);
        dto.setJuniorScore(technologyParserService.computeJuniorScore(
                offer.getTitle(), offer.getFullDescription(),
                offer.getRequiredTechnologies(), offer.getStudyLevels()));
        return dto;
    }

    @Transactional
    public JobOfferResponseDto updateStatus(Long id, JobStatus newStatus) {
        JobOffer offer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la oferta con id: " + id));
        offer.setStatus(newStatus);
        return JobOfferResponseDto.fromEntity(jobOfferRepository.save(offer));
    }

    @Transactional
    public JobOfferResponseDto markAsViewedIfNew(Long id) {
        JobOffer offer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la oferta con id: " + id));
        if (offer.getStatus() == JobStatus.NUEVA) {
            offer.setStatus(JobStatus.VISTA);
            offer = jobOfferRepository.save(offer);
        }
        return JobOfferResponseDto.fromEntity(offer);
    }

    @Transactional(readOnly = true)
    public JobStatsDto getStats() {
        long total = jobOfferRepository.count();
        long nuevas = jobOfferRepository.countByStatus(JobStatus.NUEVA);
        long vistas = jobOfferRepository.countByStatus(JobStatus.VISTA);
        long aplicadas = jobOfferRepository.countByStatus(JobStatus.APLICADA);
        long descartadas = jobOfferRepository.countByStatus(JobStatus.DESCARTADA);
        List<String> distinctTechs = jobOfferRepository.findAllDistinctTechnologies();

        return JobStatsDto.builder()
                .total(total)
                .nuevas(nuevas)
                .vistas(vistas)
                .aplicadas(aplicadas)
                .descartadas(descartadas)
                .availableTechnologies(distinctTechs)
                .build();
    }
}
