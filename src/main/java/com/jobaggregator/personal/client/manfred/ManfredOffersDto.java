package com.jobaggregator.personal.client.manfred;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManfredOffersDto {

    private List<ManfredOffer> offers;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ManfredOffer {
        private String slug;
        private String title;
        private String summary;
        private String description;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("companyUrl")
        private String companyUrl;

        @JsonProperty("remote")
        private Remote remote;

        private List<String> locations;
        private SalaryRange salary;
        private List<String> tags;
        private String createdAt;
        private String updatedAt;

        public String getUrl() {
            return "https://www.getmanfred.com/es/ofertas-empleo/" + (slug != null ? slug : "");
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Remote {
        @JsonProperty("isRemote")
        private Boolean isRemote;

        @JsonProperty("percentage")
        private Integer percentage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalaryRange {
        private Integer from;
        private Integer to;
        private String currency;
    }
}
