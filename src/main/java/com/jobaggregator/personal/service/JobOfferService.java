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

        return jobOfferRepository.findAll(spec, pageable).map(JobOfferResponseDto::fromEntity);
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

            // Hierarchical geographic filter (Smart Cascading)
            List<Predicate> geoPredicates = new ArrayList<>();

            if (province != null && !province.trim().isEmpty()) {
                // Province selected — match province + community + country
                String provNorm = "%" + normalizeForSearch(province) + "%";
                geoPredicates.add(cb.like(cb.lower(root.get("provinceOrCity")), provNorm));
                geoPredicates.add(cb.like(cb.lower(root.get("location")), provNorm));
                // If modality allows remote, include Spain-wide remote offers
                if (modality == null || modality == JobModality.REMOTO_100) {
                    geoPredicates.add(cb.and(
                            cb.equal(root.get("modality"), JobModality.REMOTO_100),
                            cb.or(
                                    cb.like(cb.lower(root.get("country")), "%españa%"),
                                    cb.like(cb.lower(root.get("country")), "%spain%"),
                                    cb.like(cb.lower(root.get("country")), "%españa%"),
                                    cb.like(cb.lower(root.get("continent")), "%global%"),
                                    cb.like(cb.lower(root.get("country")), "%mundial%"),
                                    cb.like(cb.lower(root.get("country")), "%worldwide%"),
                                    cb.like(cb.lower(root.get("country")), "%europa%"),
                                    cb.like(cb.lower(root.get("country")), "%europe%")
                            )
                    ));
                }
            } else if (community != null && !community.trim().isEmpty()) {
                // Community selected — match all its provinces
                String commNorm = "%" + normalizeForSearch(community) + "%";
                geoPredicates.add(cb.like(cb.lower(root.get("autonomousCommunity")), commNorm));
                // Also match individual province names of that community
                List<String> communityProvinces = spanishGeographyService.getSpanishGeographyTree()
                        .getOrDefault(community, Collections.emptyList());
                for (String prov : communityProvinces) {
                    String provPat = "%" + normalizeForSearch(prov) + "%";
                    geoPredicates.add(cb.like(cb.lower(root.get("provinceOrCity")), provPat));
                    geoPredicates.add(cb.like(cb.lower(root.get("location")), provPat));
                }
                // Include remote Spain/Europe if modality allows
                if (modality == null || modality == JobModality.REMOTO_100) {
                    geoPredicates.add(cb.and(
                            cb.equal(root.get("modality"), JobModality.REMOTO_100),
                            cb.or(
                                    cb.like(cb.lower(root.get("country")), "%spain%"),
                                    cb.like(cb.lower(root.get("country")), "%españa%"),
                                    cb.like(cb.lower(root.get("country")), "%worldwide%"),
                                    cb.like(cb.lower(root.get("country")), "%europa%"),
                                    cb.like(cb.lower(root.get("country")), "%europe%")
                            )
                    ));
                }
            } else if (country != null && !country.trim().isEmpty()) {
                // Country selected
                String countryNorm = "%" + normalizeForSearch(country) + "%";
                Predicate countryPred = cb.or(
                        cb.like(cb.lower(root.get("country")), countryNorm),
                        cb.like(cb.lower(root.get("location")), countryNorm)
                );
                if ("españa".equals(normalizeForSearch(country)) || "spain".equals(normalizeForSearch(country))) {
                    // Spain also includes worldwide remote
                    geoPredicates.add(cb.or(
                            countryPred,
                            cb.like(cb.lower(root.get("country")), "%spain%"),
                            cb.like(cb.lower(root.get("country")), "%españa%")
                    ));
                    if (modality == null || modality == JobModality.REMOTO_100) {
                        geoPredicates.add(cb.and(
                                cb.equal(root.get("modality"), JobModality.REMOTO_100),
                                cb.or(
                                        cb.like(cb.lower(root.get("country")), "%worldwide%"),
                                        cb.like(cb.lower(root.get("country")), "%europa%"),
                                        cb.like(cb.lower(root.get("country")), "%europe%")
                                )
                        ));
                    }
                } else {
                    geoPredicates.add(countryPred);
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
                predicates.add(cb.or(geoPredicates.toArray(new Predicate[0])));
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
        return JobOfferResponseDto.fromEntity(offer);
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
