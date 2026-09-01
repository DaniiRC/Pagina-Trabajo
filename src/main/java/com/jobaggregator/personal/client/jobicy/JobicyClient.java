package com.jobaggregator.personal.client.jobicy;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobicyClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;

    @Value("${jobs.jobicy.enabled:true}")
    private boolean enabled;

    private static final List<String> ENDPOINTS = List.of(
            "https://jobicy.com/api/v2/remote-jobs?count=30&geo=spain",
            "https://jobicy.com/api/v2/remote-jobs?count=30&geo=emea",
            "https://jobicy.com/api/v2/remote-jobs?count=30&industry=engineering"
    );

    @Override
    public JobSource getSource() {
        return JobSource.JOBICY;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Jobicy client is disabled.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (String endpoint : ENDPOINTS) {
            try {
                log.info("Fetching jobs from Jobicy API: {}", endpoint);
                JobicyResponseDto response = restClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .body(JobicyResponseDto.class);

                if (response != null && response.getData() != null) {
                    for (JobicyResponseDto.JobicyItem item : response.getData()) {
                        if (item.getUrl() == null || item.getUrl().isBlank() || seenUrls.contains(item.getUrl())) {
                            continue;
                        }
                        seenUrls.add(item.getUrl());

                        JobOffer offer = mapToJobOffer(item);
                        if (offer != null) {
                            results.add(offer);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error fetching jobs from Jobicy endpoint {}: {}", endpoint, e.getMessage());
            }
        }

        log.info("Jobicy ingestion finished. Total items fetched: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(JobicyResponseDto.JobicyItem item) {
        String cleanShort = technologyParserService.cleanHtmlDescription(item.getJobDescription());
        String cleanFull = technologyParserService.cleanFullDescription(item.getJobDescription());
        Set<String> techs = technologyParserService.extractTechnologies(
                item.getJobTitle(),
                cleanFull,
                item.getJobType()
        );

        LocalDateTime pubDate = parseDate(item.getPubDate());
        String location = (item.getJobGeo() != null && !item.getJobGeo().isBlank()) 
                ? item.getJobGeo().trim() 
                : "España / Remoto";

        return JobOffer.builder()
                .externalId(item.getId() != null ? String.valueOf(item.getId()) : UUID.randomUUID().toString())
                .title(item.getJobTitle() != null ? item.getJobTitle().trim() : "Oferta Técnica")
                .companyName(item.getCompanyName() != null ? item.getCompanyName().trim() : "Empresa Confidencial")
                .shortDescription(cleanShort)
                .fullDescription(cleanFull)
                .url(item.getUrl().trim())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .status(JobStatus.NUEVA)
                .source(JobSource.JOBICY)
                .isRemote(true)
                .location(location)
                .build();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }
}
