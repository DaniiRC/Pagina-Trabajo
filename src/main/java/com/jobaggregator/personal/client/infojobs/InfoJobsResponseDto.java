package com.jobaggregator.personal.client.infojobs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfoJobsResponseDto {

    private Integer totalResults;
    private Integer totalPages;
    private List<InfoJobsOfferItem> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoJobsOfferItem {
        private String id;
        private String title;
        private String link;
        private String published;
        private String updated;
        private Author author;
        private ValueItem province;
        private String city;
        private ValueItem teleworking;
        private ValueItem contractType;
        private ValueItem workDay;
        private ValueItem study;
        private ValueItem requirementMinStudies;
        private ValueItem experienceMin;
        private Salary salaryDescription;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String id;
        private String name;
        private String uri;
        private String logoUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValueItem {
        private String id;
        private String value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Salary {
        private String value;
    }
}
