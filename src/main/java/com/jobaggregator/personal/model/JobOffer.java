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
        @Index(name = "idx_job_published_date", columnList = "published_date")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private JobStatus status = JobStatus.NUEVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobSource source;

    @Column(name = "is_remote")
    @Builder.Default
    private Boolean isRemote = true;

    @Column(length = 255)
    private String location;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
