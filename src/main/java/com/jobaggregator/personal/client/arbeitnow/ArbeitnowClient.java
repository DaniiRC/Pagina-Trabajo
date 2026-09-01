package com.jobaggregator.personal.client.arbeitnow;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArbeitnowClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    @Value("${jobs.arbeitnow.enabled:true}")
    private boolean enabled;

    private static final String API_URL = "https://www.arbeitnow.com/api/job-board-api";

    @Override
    public JobSource getSource() {
        return JobSource.ARBEITNOW;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Arbeitnow client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        try {
            log.info("Fetching jobs from Arbeitnow API: {}", API_URL);

            ArbeitnowResponseDto response = restClient.get()
                    .uri(API_URL)
                    .retrieve()
                    .body(ArbeitnowResponseDto.class);

            if (response != null && response.getData() != null) {
                for (ArbeitnowResponseDto.ArbeitnowJobItem item : response.getData()) {
                    if (item.getUrl() == null || item.getUrl().isBlank()) {
                        continue;
                    }

                    JobOffer offer = mapToJobOffer(item);
                    if (offer != null) {
                        results.add(offer);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching jobs from Arbeitnow API: {}", e.getMessage());
        }

        log.info("Arbeitnow ingestion finished. Total items fetched: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(ArbeitnowResponseDto.ArbeitnowJobItem item) {
        String cleanDescription = technologyParserService.cleanHtmlDescription(item.getDescription());
        String fullDescription = technologyParserService.cleanFullDescription(item.getDescription());
        Set<String> techs = technologyParserService.extractTechnologies(item.getTitle(), cleanDescription, item.getTags());
        Set<String> studies = technologyParserService.extractStudyLevels(item.getTitle(), fullDescription, techs);
        boolean remote = item.getRemote() != null ? item.getRemote() : true;
        String rawLoc = item.getLocation() != null ? item.getLocation() : (remote ? "Remote / Europe" : "Europa");
        JobModality modality = technologyParserService.inferModality(remote, rawLoc, fullDescription);
        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(rawLoc, item.getTitle(), fullDescription, remote);

        LocalDateTime pubDate = parseTimestamp(item.getCreatedAt());

        return JobOffer.builder()
                .externalId(item.getSlug() != null ? item.getSlug() : UUID.randomUUID().toString())
                .title(item.getTitle() != null ? item.getTitle().trim() : "Oferta sin título")
                .companyName(item.getCompanyName() != null ? item.getCompanyName().trim() : "Empresa confidencial")
                .shortDescription(cleanDescription)
                .fullDescription(fullDescription)
                .url(item.getUrl().trim())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.ARBEITNOW)
                .modality(modality)
                .isRemote(remote)
                .location(rawLoc)
                .continent(geo.continent())
                .country(geo.country())
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .build();
    }

    private LocalDateTime parseTimestamp(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
