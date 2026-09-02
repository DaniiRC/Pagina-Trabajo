package com.jobaggregator.personal.client.adzuna;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdzunaResponseDto {

    @JsonProperty("results")
    private List<AdzunaJob> results;

    @JsonProperty("count")
    private Long count;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaJob {

        private String id;
        private String title;
        private String description;

        @JsonProperty("redirect_url")
        private String redirectUrl;

        @JsonProperty("created")
        private String created;

        @JsonProperty("company")
        private NamedValue company;

        @JsonProperty("location")
        private AdzunaLocation location;

        @JsonProperty("salary_min")
        private Double salaryMin;

        @JsonProperty("salary_max")
        private Double salaryMax;

        @JsonProperty("salary_is_predicted")
        private String salaryIsPredicted;

        @JsonProperty("contract_time")
        private String contractTime;

        @JsonProperty("contract_type")
        private String contractType;

        @JsonProperty("category")
        private NamedValue category;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaLocation {
        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("area")
        private List<String> area;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamedValue {
        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("label")
        private String label;
    }
}
