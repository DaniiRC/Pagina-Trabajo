package com.jobaggregator.personal.repository;

import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    boolean existsByUrl(String url);

    boolean existsBySourceAndExternalId(JobSource source, String externalId);

    Optional<JobOffer> findByUrl(String url);

    long countByStatus(JobStatus status);

    @Query("SELECT j FROM JobOffer j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(j.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY j.publishedDate DESC NULLS LAST, j.createdAt DESC")
    Page<JobOffer> findWithFilters(
            @Param("status") JobStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT DISTINCT tech FROM JobOffer j JOIN j.requiredTechnologies tech ORDER BY tech ASC")
    List<String> findAllDistinctTechnologies();
}
