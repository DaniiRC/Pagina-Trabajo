package com.jobaggregator.personal.client.jobicy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobicyResponseDto {

    private Boolean success;
    private List<JobicyItem> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobicyItem {
        private Long id;
        private String url;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("jobDescription")
        private String jobDescription;

        @JsonProperty("jobGeo")
        private String jobGeo;

        @JsonProperty("jobType")
        private List<String> jobType;

        @JsonProperty("pubDate")
        private String pubDate;
    }
}
