package com.jobaggregator.personal.client.adzuna;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Client for the Adzuna public API (Spain).
 * Register free at https://developer.adzuna.com to get APP_ID + APP_KEY.
 * Free tier: 250 req/day. No credit card needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdzunaClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    @Value("${adzuna.app.id:}")
    private String appId;

    @Value("${adzuna.app.key:}")
    private String appKey;

    @Value("${jobs.adzuna.enabled:true}")
    private boolean enabled;

    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs/es/search/1";

    // Consolidated tech-focused search terms for Spain
    private static final List<String> SEARCH_TERMS = List.of(
            "desarrollador software programador",
            "frontend backend fullstack developer",
            "devops cloud sistemas linux",
            "ciberseguridad redes informatica"
    );

    private volatile String lastStatus = "Pendiente de sincronizar";

    @Override
    public String getDetailedStatus() {
        if (!enabled) {
            return "Desactivado en configuración";
        }
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            return "Sin credenciales: falta ADZUNA_APP_ID / KEY en Render";
        }
        return lastStatus;
    }

    @Override
    public JobSource getSource() {
        return JobSource.ADZUNA;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            lastStatus = "Desactivado en configuración";
            log.info("Adzuna client disabled.");
            return Collections.emptyList();
        }
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            lastStatus = "Sin credenciales: falta ADZUNA_APP_ID / KEY en Render";
            log.info("Adzuna disabled: ADZUNA_APP_ID / ADZUNA_APP_KEY not configured. Register free at https://developer.adzuna.com");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String term : SEARCH_TERMS) {
            try {
                String uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                        .queryParam("app_id", appId.trim())
                        .queryParam("app_key", appKey.trim())
                        .queryParam("results_per_page", 25)
                        .queryParam("what", term)
                        .queryParam("content-type", "application/json")
                        .build()
                        .toUriString();

                log.info("Fetching Adzuna ES jobs for term: '{}'", term);
                AdzunaResponseDto response = restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(AdzunaResponseDto.class);

                if (response == null || response.getResults() == null) continue;

                for (AdzunaResponseDto.AdzunaJob item : response.getResults()) {
                    if (item.getId() == null || seenIds.contains(item.getId())) continue;
                    seenIds.add(item.getId());

                    JobOffer offer = mapToJobOffer(item);
                    if (offer != null) results.add(offer);
                }

                // Rate limit protection
                Thread.sleep(150);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Error fetching Adzuna for '{}': {}", term, e.getMessage());
            }
        }

        lastStatus = results.size() + " ofertas obtenidas en España";
        log.info("Adzuna ingestion finished. Total: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(AdzunaResponseDto.AdzunaJob item) {
        String companyName = item.getCompany() != null ? item.getCompany().getDisplayName() : "Empresa Confidencial";
        String rawLocation = buildLocation(item);
        String cleanDesc = technologyParserService.cleanHtmlDescription(item.getDescription());
        String fullDesc = technologyParserService.cleanFullDescription(item.getDescription());

        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(rawLocation, item.getTitle(), cleanDesc, null);

        Set<String> techs = technologyParserService.extractTechnologies(item.getTitle(), fullDesc, null);
        Set<String> studies = technologyParserService.extractStudyLevels(item.getTitle(), fullDesc, techs);
        JobModality modality = inferModality(item, fullDesc);

        LocalDateTime pubDate = parseDate(item.getCreated());

        return JobOffer.builder()
                .externalId("adzuna-" + item.getId())
                .title(item.getTitle())
                .companyName(companyName)
                .shortDescription(cleanDesc)
                .fullDescription(fullDesc)
                .url(item.getRedirectUrl())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.ADZUNA)
                .modality(modality)
                .isRemote(modality == JobModality.REMOTO_100)
                .location(rawLocation)
                .continent("Europa")
                .country("España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .salaryMin(item.getSalaryMin())
                .salaryMax(item.getSalaryMax())
                .salaryCurrency("EUR")
                .build();
    }

    private String buildLocation(AdzunaResponseDto.AdzunaJob item) {
        if (item.getLocation() != null && item.getLocation().getDisplayName() != null) {
            return item.getLocation().getDisplayName() + ", España";
        }
        return "España";
    }

    private JobModality inferModality(AdzunaResponseDto.AdzunaJob item, String desc) {
        String combined = (item.getTitle() + " " + desc).toLowerCase();
        if (combined.contains("100% remoto") || combined.contains("teletrabajo") || combined.contains("100% teletrabaijo")) {
            return JobModality.REMOTO_100;
        }
        if (combined.contains("hibrido") || combined.contains("híbrido") || combined.contains("hybrid")) {
            return JobModality.HIBRIDO;
        }
        return technologyParserService.inferModality(null, buildLocation(item), desc);
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
