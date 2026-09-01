package com.jobaggregator.personal.service;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.dto.JobStatsDto;
import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.repository.JobOfferRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;

    @Transactional(readOnly = true)
    public Page<JobOfferResponseDto> getOffers(
            JobStatus status,
            String keyword,
            String technology,
            Boolean isRemote,
            String location,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("publishedDate"), Sort.Order.desc("id"))
        );

        Specification<JobOffer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate companyMatch = cb.like(cb.lower(root.get("companyName")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("shortDescription")), pattern);
                predicates.add(cb.or(titleMatch, companyMatch, descMatch));
            }

            if (technology != null && !technology.trim().isEmpty()) {
                Join<JobOffer, String> techJoin = root.join("requiredTechnologies");
                predicates.add(cb.equal(cb.lower(techJoin), technology.trim().toLowerCase()));
            }

            if (isRemote != null) {
                predicates.add(cb.equal(root.get("isRemote"), isRemote));
            }

            if (location != null && !location.trim().isEmpty()) {
                String rawLoc = location.trim().toLowerCase();
                List<Predicate> locPredicates = new ArrayList<>();

                // Direct match
                locPredicates.add(cb.like(cb.lower(root.get("location")), "%" + rawLoc + "%"));

                // Spanish regional intelligence
                if (rawLoc.contains("jaen") || rawLoc.contains("jaén")) {
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%jaen%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%jaén%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%andaluc%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%spain%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%españa%"));
                    // Remote positions eligible from Jaén
                    if (isRemote == null || isRemote) {
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%world%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%europe%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%emea%")));
                    }
                } else if (rawLoc.contains("andaluc")) {
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%andaluc%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%jaen%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%jaén%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%sevilla%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%malaga%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%málaga%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%granada%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%cordoba%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%córdoba%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%spain%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%españa%"));
                    if (isRemote == null || isRemote) {
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%world%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%europe%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%emea%")));
                    }
                } else if (rawLoc.contains("españa") || rawLoc.contains("spain")) {
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%spain%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%españa%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%madrid%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%barcelona%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%valencia%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%andaluc%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%sevilla%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%malaga%"));
                    locPredicates.add(cb.like(cb.lower(root.get("location")), "%bilbao%"));
                    if (isRemote == null || isRemote) {
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%world%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%europe%")));
                        locPredicates.add(cb.and(cb.equal(root.get("isRemote"), true), cb.like(cb.lower(root.get("location")), "%emea%")));
                    }
                }

                predicates.add(cb.or(locPredicates.toArray(new Predicate[0])));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return jobOfferRepository.findAll(spec, pageable).map(JobOfferResponseDto::fromEntity);
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
        JobOffer saved = jobOfferRepository.save(offer);
        log.info("JobOffer id={} status updated to {}", id, newStatus);
        return JobOfferResponseDto.fromEntity(saved);
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
