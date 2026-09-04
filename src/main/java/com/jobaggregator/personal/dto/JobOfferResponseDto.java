package com.jobaggregator.personal.dto;

import com.jobaggregator.personal.model.JobModality;
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
    private String fullDescription;
    private String url;
    private LocalDateTime publishedDate;
    private Set<String> requiredTechnologies;
    private Set<String> studyLevels;
    private JobStatus status;
    private JobSource source;
    private JobModality modality;
    private Boolean isRemote;
    private String location;
    private String continent;
    private String country;
    private String autonomousCommunity;
    private String provinceOrCity;
    private Double salaryMin;
    private Double salaryMax;
    private String salaryCurrency;
    private LocalDateTime createdAt;
    /** 0-100 affinity score for junior DAM/DAW/ASIR profile. Set by JobOfferService. */
    private Integer juniorScore;

    public static JobOfferResponseDto fromEntity(JobOffer entity) {
        if (entity == null) return null;
        return JobOfferResponseDto.builder()
                .id(entity.getId())
                .externalId(entity.getExternalId())
                .title(entity.getTitle())
                .companyName(entity.getCompanyName())
                .shortDescription(entity.getShortDescription())
                .fullDescription(entity.getFullDescription() != null ? entity.getFullDescription() : entity.getShortDescription())
                .url(entity.getUrl())
                .publishedDate(entity.getPublishedDate())
                .requiredTechnologies(entity.getRequiredTechnologies())
                .studyLevels(entity.getStudyLevels())
                .status(entity.getStatus())
                .source(entity.getSource())
                .modality(entity.getModality())
                .isRemote(entity.getIsRemote())
                .location(entity.getLocation())
                .continent(entity.getContinent())
                .country(entity.getCountry())
                .autonomousCommunity(entity.getAutonomousCommunity())
                .provinceOrCity(entity.getProvinceOrCity())
                .salaryMin(entity.getSalaryMin())
                .salaryMax(entity.getSalaryMax())
                .salaryCurrency(entity.getSalaryCurrency())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
