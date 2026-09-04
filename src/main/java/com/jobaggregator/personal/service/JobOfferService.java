package com.jobaggregator.personal.service;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.dto.JobStatsDto;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.repository.JobOfferRepository;
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

            // Keyword search
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern),
                        cb.like(cb.lower(root.get("shortDescription")), pattern)
                ));
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

                    // Predicado negativo: country NO es otro país conocido
                    List<String> otherCountries = List.of(
                            "reino unido", "uk", "united kingdom",
                            "alemania", "germany", "deutschland",
                            "francia", "france",
                            "estados unidos", "usa", "united states",
                            "polonia", "poland",
                            "italia", "italy",
                            "países bajos", "netherlands", "holanda",
                            "portugal", "brasil", "brazil",
                            "argentina", "méxico", "mexico",
                            "colombia", "chile", "peru",
                            "rumania", "romania",
                            "irlanda", "ireland",
                            "suecia", "sweden",
                            "noruega", "norway",
                            "dinamarca", "denmark"
                    );
                    List<Predicate> notOtherPreds = new ArrayList<>();
                    for (String other : otherCountries) {
                        notOtherPreds.add(cb.notEqual(cb.lower(root.get("country")), other));
                    }
                    Predicate notOtherCountry = cb.and(notOtherPreds.toArray(new Predicate[0]));

                    geoPredicates.add(cb.and(isSpain, notOtherCountry));

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
                // Generic location text fallback
                String locPat = "%" + normalizeForSearch(location) + "%";
                geoPredicates.add(cb.or(
                        cb.like(cb.lower(root.get("location")), locPat),
                        cb.like(cb.lower(root.get("country")), locPat),
                        cb.like(cb.lower(root.get("autonomousCommunity")), locPat),
                        cb.like(cb.lower(root.get("provinceOrCity")), locPat)
                ));
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
