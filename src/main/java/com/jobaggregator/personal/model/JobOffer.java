package com.jobaggregator.personal.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "job_offers",
    indexes = {
        @Index(name = "idx_job_url", columnList = "url", unique = true),
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_source_ext", columnList = "source, external_id"),
        @Index(name = "idx_job_published_date", columnList = "published_date"),
        @Index(name = "idx_job_country", columnList = "country"),
        @Index(name = "idx_job_community", columnList = "autonomous_community"),
        @Index(name = "idx_job_province", columnList = "province_or_city"),
        @Index(name = "idx_job_modality", columnList = "modality")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "full_description", columnDefinition = "TEXT")
    private String fullDescription;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(name = "published_date")
    private LocalDateTime publishedDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "job_offer_technologies",
        joinColumns = @JoinColumn(name = "job_offer_id")
    )
    @Column(name = "technology")
    @Builder.Default
    private Set<String> requiredTechnologies = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "job_offer_studies",
        joinColumns = @JoinColumn(name = "job_offer_id")
    )
    @Column(name = "study_level")
    @Builder.Default
    private Set<String> studyLevels = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private JobStatus status = JobStatus.NUEVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobSource source;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private JobModality modality = JobModality.REMOTO_100;

    @Column(name = "is_remote")
    @Builder.Default
    private Boolean isRemote = true;

    @Column(length = 255)
    private String location;

    // Hierarchical geographic locations
    @Column(length = 100)
    private String continent;

    @Column(length = 100)
    private String country;

    @Column(name = "autonomous_community", length = 100)
    private String autonomousCommunity;

    @Column(name = "province_or_city", length = 150)
    private String provinceOrCity;

    // Optional salary info
    @Column(name = "salary_min")
    private Double salaryMin;

    @Column(name = "salary_max")
    private Double salaryMax;

    @Column(name = "salary_currency", length = 10)
    private String salaryCurrency;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
