package com.jobaggregator.personal.client.linkedin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCustomSearchResponseDto {

    private SearchInformation searchInformation;
    private List<SearchItem> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchInformation {
        private String totalResults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchItem {
        private String title;
        private String link;
        private String snippet;
        private String formattedUrl;
        private Map<String, Object> pagemap;
    }
}
