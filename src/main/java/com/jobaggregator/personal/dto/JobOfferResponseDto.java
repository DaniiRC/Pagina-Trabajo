package com.jobaggregator.personal.dto;

import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobOfferResponseDto {
    private Long id;
    private String externalId;
    private String title;
    private String companyName;
    private String shortDescription;
    private String url;
    private LocalDateTime publishedDate;
    private Set<String> requiredTechnologies;
    private JobStatus status;
    private JobSource source;
    private Boolean isRemote;
    private String location;
    private LocalDateTime createdAt;

    public static JobOfferResponseDto fromEntity(JobOffer entity) {
        if (entity == null) return null;
        return JobOfferResponseDto.builder()
                .id(entity.getId())
                .externalId(entity.getExternalId())
                .title(entity.getTitle())
                .companyName(entity.getCompanyName())
                .shortDescription(entity.getShortDescription())
                .url(entity.getUrl())
                .publishedDate(entity.getPublishedDate())
                .requiredTechnologies(entity.getRequiredTechnologies())
                .status(entity.getStatus())
                .source(entity.getSource())
                .isRemote(entity.getIsRemote())
                .location(entity.getLocation())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
