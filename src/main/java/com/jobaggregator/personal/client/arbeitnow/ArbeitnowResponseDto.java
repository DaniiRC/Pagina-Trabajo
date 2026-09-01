package com.jobaggregator.personal.client.arbeitnow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArbeitnowResponseDto {

    private List<ArbeitnowJobItem> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArbeitnowJobItem {
        private String slug;

        @JsonProperty("company_name")
        private String companyName;

        private String title;
        private String description;
        private Boolean remote;
        private String url;
        private List<String> tags;

        @JsonProperty("job_types")
        private List<String> jobTypes;

        private String location;

        @JsonProperty("created_at")
        private Long createdAt; // Unix timestamp in seconds
    }
}
