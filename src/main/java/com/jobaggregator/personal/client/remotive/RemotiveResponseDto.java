package com.jobaggregator.personal.client.remotive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemotiveResponseDto {

    @JsonProperty("job-count")
    private Integer jobCount;

    @JsonProperty("jobs")
    private List<RemotiveJobItem> jobs;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemotiveJobItem {
        private Long id;
        private String url;
        private String title;

        @JsonProperty("company_name")
        private String companyName;

        private String category;
        private List<String> tags;

        @JsonProperty("job_type")
        private String jobType;

        @JsonProperty("publication_date")
        private String publicationDate;

        @JsonProperty("candidate_required_location")
        private String candidateRequiredLocation;

        private String salary;
        private String description;
    }
}
