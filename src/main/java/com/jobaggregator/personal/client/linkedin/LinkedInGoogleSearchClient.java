package com.jobaggregator.personal.client.linkedin;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.StudyKeywordMapperService;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkedInGoogleSearchClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;
    private final StudyKeywordMapperService studyKeywordMapperService;

    @Value("${google.search.api.key:${GOOGLE_SEARCH_API_KEY:}}")
    private String googleApiKey;

    @Value("${google.search.cx:${GOOGLE_SEARCH_CX:}}")
    private String googleSearchEngineId;

    @Value("${jobs.linkedin.enabled:true}")
    private boolean enabled;

    private static final String GOOGLE_SEARCH_URL = "https://customsearch.googleapis.com/customsearch/v1";

    private volatile String lastStatus = "Pendiente de sincronizar";

    @Override
    public String getDetailedStatus() {
        if (!enabled) {
            return "Desactivado en configuración";
        }
        boolean hasGoogleCredentials = googleApiKey != null && !googleApiKey.isBlank()
                && googleSearchEngineId != null && !googleSearchEngineId.isBlank();
        if (!hasGoogleCredentials) {
            return "Sin credenciales: falta GOOGLE_SEARCH_API_KEY / CX en Render";
        }
        return lastStatus;
    }

    @Override
    public JobSource getSource() {
        return JobSource.LINKEDIN;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            lastStatus = "Desactivado en configuración";
            log.info("LinkedIn/GoogleSearch client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        boolean hasGoogleCredentials = googleApiKey != null && !googleApiKey.isBlank()
                && googleSearchEngineId != null && !googleSearchEngineId.isBlank();

        if (!hasGoogleCredentials) {
            lastStatus = "Sin credenciales: falta GOOGLE_SEARCH_API_KEY / CX en Render";
            log.info("LinkedIn client: GOOGLE_SEARCH_API_KEY and GOOGLE_SEARCH_CX are not set in environment. Skipping LinkedIn to prevent IP blocking. Set them in Render to enable.");
            return Collections.emptyList();
        }

        Map<String, String> studyProfiles = studyKeywordMapperService.getActiveStudyMappings();

        for (Map.Entry<String, String> entry : studyProfiles.entrySet()) {
            String studyName = entry.getKey();
            String keywords = entry.getValue();

            try {
                fetchViaGoogleCustomSearch(studyName, keywords, results, seenUrls);
                Thread.sleep(200); // Politeness delay between queries
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching LinkedIn jobs for study profile '{}': {}", studyName, e.getMessage());
            }
        }

        lastStatus = results.size() + " ofertas obtenidas vía Google Custom Search";
        log.info("LinkedIn ingestion finished. Total offers retrieved: {}", results.size());
        return results;
    }

    /**
     * Modalidad oficial: Búsqueda mediante Google Custom Search API
     * Query: site:es.linkedin.com/jobs <keywords> España
     */
    private void fetchViaGoogleCustomSearch(String studyName, String keywords, List<JobOffer> results, Set<String> seenUrls) {
        String query = "site:es.linkedin.com/jobs " + keywords + " España";
        log.info("Querying Google Custom Search for LinkedIn jobs: '{}'", query);

        try {
            String uri = UriComponentsBuilder.fromHttpUrl(GOOGLE_SEARCH_URL)
                    .queryParam("key", googleApiKey.trim())
                    .queryParam("cx", googleSearchEngineId.trim())
                    .queryParam("q", query)
                    .queryParam("gl", "es")
                    .queryParam("lr", "lang_es")
                    .queryParam("num", 10)
                    .build()
                    .toUriString();

            GoogleCustomSearchResponseDto response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(GoogleCustomSearchResponseDto.class);

            if (response == null || response.getItems() == null) {
                return;
            }

            for (GoogleCustomSearchResponseDto.SearchItem item : response.getItems()) {
                String link = item.getLink();
                if (link == null || link.isBlank() || seenUrls.contains(link.trim())) {
                    continue;
                }
                seenUrls.add(link.trim());

                JobOffer offer = mapGoogleItemToJobOffer(item, studyName);
                if (offer != null) {
                    results.add(offer);
                }
            }
        } catch (Exception e) {
            log.error("Google Custom Search API error for '{}': {}", studyName, e.getMessage());
        }
    }

    private JobOffer mapGoogleItemToJobOffer(GoogleCustomSearchResponseDto.SearchItem item, String studyName) {
        String title = item.getTitle();
        String link = item.getLink();
        String snippet = item.getSnippet() != null ? item.getSnippet() : "";

        // Parse "Título - Empresa - Ubicación | LinkedIn" format commonly returned by Google
        String parsedTitle = title;
        String company = "Empresa en LinkedIn";
        String location = "España";

        if (title.contains(" - ")) {
            String[] parts = title.split(" - ");
            parsedTitle = parts[0].trim();
            if (parts.length > 1) {
                company = parts[1].replace("| LinkedIn", "").trim();
            }
            if (parts.length > 2) {
                location = parts[2].replace("| LinkedIn", "").trim();
            }
        } else if (title.contains(" | LinkedIn")) {
            parsedTitle = title.replace(" | LinkedIn", "").trim();
        }

        return buildLinkedInJobOffer(link, parsedTitle, company, location, studyName);
    }

    private JobOffer buildLinkedInJobOffer(String link, String title, String company, String rawLocation, String studyName) {
        String cleanTitle = title.replaceAll("\\s+", " ").trim();
        String cleanLocation = rawLocation != null && !rawLocation.isBlank() ? rawLocation.replaceAll("\\s+", " ").trim() : "España";

        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                cleanLocation, cleanTitle, null, false
        );

        Set<String> techs = technologyParserService.extractTechnologies(cleanTitle, cleanLocation, null);
        Set<String> studies = new HashSet<>(technologyParserService.extractStudyLevels(cleanTitle, cleanLocation, techs));
        if (studyName != null && !studyName.isBlank()) {
            studies.add(studyName.toUpperCase());
        }

        JobModality modality = technologyParserService.inferModality(null, cleanLocation, cleanTitle);

        String id = link;
        if (link.contains("/view/")) {
            id = link.substring(link.indexOf("/view/") + 6).replaceAll("[^0-9]", "");
        }
        if (id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        return JobOffer.builder()
                .externalId("linkedin-" + id)
                .title(cleanTitle)
                .companyName(company)
                .shortDescription("Oferta técnica publicada en LinkedIn — " + cleanLocation)
                .fullDescription("Oferta de empleo en LinkedIn para el puesto: " + cleanTitle + " en " + company + " (" + cleanLocation + ").\n\nConsulta los requisitos completos y postúlate directamente en LinkedIn a través del enlace oficial.")
                .url(link)
                .publishedDate(LocalDateTime.now())
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.LINKEDIN)
                .modality(modality)
                .isRemote(modality == JobModality.REMOTO_100)
                .location(cleanLocation.contains("España") ? cleanLocation : cleanLocation + ", España")
                .continent(geo.continent() != null ? geo.continent() : "Europa")
                .country("España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .build();
    }
}
