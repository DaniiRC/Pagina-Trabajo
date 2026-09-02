package com.jobaggregator.personal.client.manfred;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManfredClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    @Value("${jobs.manfred.enabled:false}")
    private boolean enabled;

    // GetManfred does not have a public open API. Disable by default.
    private static final String API_URL = "https://www.getmanfred.com/api/v1/offers?lang=es&status=open";

    @Override
    public JobSource getSource() {
        return JobSource.MANFRED;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Manfred client disabled.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();

        try {
            log.info("Fetching offers from GetManfred API...");
            ManfredOffersDto response = restClient.get()
                    .uri(API_URL)
                    .retrieve()
                    .body(ManfredOffersDto.class);

            if (response == null || response.getOffers() == null) {
                log.warn("Manfred API returned empty response");
                return results;
            }

            Set<String> seenUrls = new HashSet<>();
            for (ManfredOffersDto.ManfredOffer offer : response.getOffers()) {
                String url = offer.getUrl();
                if (url.isBlank() || seenUrls.contains(url)) continue;
                seenUrls.add(url);

                JobOffer job = mapToJobOffer(offer);
                if (job != null) results.add(job);
            }

        } catch (Exception e) {
            log.error("Error fetching Manfred offers: {}", e.getMessage());
        }

        log.info("Manfred ingestion finished. Total: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(ManfredOffersDto.ManfredOffer offer) {
        String rawLocation = buildLocation(offer);
        boolean isRemote = offer.getRemote() != null && Boolean.TRUE.equals(offer.getRemote().getIsRemote());

        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                rawLocation, offer.getTitle(), offer.getSummary(), isRemote
        );

        String descText = offer.getSummary() != null ? offer.getSummary() : offer.getTitle();
        String cleanShort = technologyParserService.cleanHtmlDescription(descText);
        String cleanFull = technologyParserService.cleanFullDescription(
                offer.getDescription() != null ? offer.getDescription() : descText
        );

        Set<String> techs = technologyParserService.extractTechnologies(offer.getTitle(), cleanFull, offer.getTags());
        Set<String> studies = technologyParserService.extractStudyLevels(offer.getTitle(), cleanFull, techs);
        JobModality modality = inferManfredModality(offer);

        Double salaryMin = null, salaryMax = null;
        String currency = null;
        if (offer.getSalary() != null) {
            salaryMin = offer.getSalary().getFrom() != null ? offer.getSalary().getFrom().doubleValue() : null;
            salaryMax = offer.getSalary().getTo() != null ? offer.getSalary().getTo().doubleValue() : null;
            currency = offer.getSalary().getCurrency();
        }

        LocalDateTime pubDate = parseDate(offer.getCreatedAt());

        return JobOffer.builder()
                .externalId(offer.getSlug())
                .title(offer.getTitle())
                .companyName(offer.getCompanyName() != null ? offer.getCompanyName() : "Empresa Confidencial")
                .shortDescription(cleanShort)
                .fullDescription(cleanFull)
                .url(offer.getUrl())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.MANFRED)
                .modality(modality)
                .isRemote(isRemote)
                .location(rawLocation)
                .continent(geo.continent() != null ? geo.continent() : "Europa")
                .country(geo.country() != null ? geo.country() : "España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .salaryCurrency(currency != null ? currency : "EUR")
                .build();
    }

    private String buildLocation(ManfredOffersDto.ManfredOffer offer) {
        if (offer.getLocations() != null && !offer.getLocations().isEmpty()) {
            return String.join(", ", offer.getLocations());
        }
        if (offer.getRemote() != null && Boolean.TRUE.equals(offer.getRemote().getIsRemote())) {
            return "Remoto, España";
        }
        return "España";
    }

    private JobModality inferManfredModality(ManfredOffersDto.ManfredOffer offer) {
        if (offer.getRemote() == null) return JobModality.PRESENCIAL;
        Integer pct = offer.getRemote().getPercentage();
        if (pct != null) {
            if (pct >= 100) return JobModality.REMOTO_100;
            if (pct >= 30) return JobModality.HIBRIDO;
        }
        return Boolean.TRUE.equals(offer.getRemote().getIsRemote()) ? JobModality.REMOTO_100 : JobModality.PRESENCIAL;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(dateStr).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
