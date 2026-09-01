package com.jobaggregator.personal.client.remotive;

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
public class RemotiveClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;

    @Value("${jobs.remotive.enabled:true}")
    private boolean enabled;

    @Value("${jobs.remotive.categories:software-dev,devops-sysadmin}")
    private List<String> categories;

    private static final String BASE_URL = "https://remotive.com/api/remote-jobs";

    @Override
    public JobSource getSource() {
        return JobSource.REMOTIVE;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Remotive client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (String category : categories) {
            try {
                String uri = BASE_URL + "?category=" + category.trim() + "&limit=30";
                log.info("Fetching jobs from Remotive API: {}", uri);

                RemotiveResponseDto response = restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(RemotiveResponseDto.class);

                if (response != null && response.getJobs() != null) {
                    for (RemotiveResponseDto.RemotiveJobItem item : response.getJobs()) {
                        if (item.getUrl() == null || seenUrls.contains(item.getUrl())) {
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
                log.error("Error fetching jobs from Remotive category {}: {}", category, e.getMessage());
            }
        }

        log.info("Remotive ingestion finished. Total items fetched: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(RemotiveResponseDto.RemotiveJobItem item) {
        String cleanDescription = technologyParserService.cleanHtmlDescription(item.getDescription());
        String fullDescription = technologyParserService.cleanFullDescription(item.getDescription());
        Set<String> techs = technologyParserService.extractTechnologies(
                item.getTitle(),
                cleanDescription,
                item.getTags()
        );

        LocalDateTime pubDate = parseDate(item.getPublicationDate());

        return JobOffer.builder()
                .externalId(item.getId() != null ? String.valueOf(item.getId()) : null)
                .title(item.getTitle() != null ? item.getTitle().trim() : "Oferta sin título")
                .companyName(item.getCompanyName() != null ? item.getCompanyName().trim() : "Empresa confidencial")
                .shortDescription(cleanDescription)
                .fullDescription(fullDescription)
                .url(item.getUrl().trim())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .status(JobStatus.NUEVA)
                .source(JobSource.REMOTIVE)
                .isRemote(true)
                .location(item.getCandidateRequiredLocation() != null ? item.getCandidateRequiredLocation() : "Remote")
                .build();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }
}
