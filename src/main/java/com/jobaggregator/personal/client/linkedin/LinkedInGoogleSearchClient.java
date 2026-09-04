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
    private static final String LINKEDIN_GUEST_API = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";

    @Override
    public JobSource getSource() {
        return JobSource.LINKEDIN;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("LinkedIn/GoogleSearch client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        boolean hasGoogleCredentials = googleApiKey != null && !googleApiKey.isBlank()
                && googleSearchEngineId != null && !googleSearchEngineId.isBlank();

        Map<String, String> studyProfiles = studyKeywordMapperService.getAllStudyMappings();

        boolean guestApiBlocked = false;

        for (Map.Entry<String, String> entry : studyProfiles.entrySet()) {
            String studyName = entry.getKey();
            String keywords = entry.getValue();

            if (guestApiBlocked && !hasGoogleCredentials) {
                break;
            }

            try {
                if (hasGoogleCredentials) {
                    fetchViaGoogleCustomSearch(studyName, keywords, results, seenUrls);
                } else {
                    boolean success = fetchViaLinkedInGuestApi(studyName, keywords, results, seenUrls);
                    if (!success) {
                        guestApiBlocked = true;
                        log.warn("LinkedIn guest search is rate-limited or blocked on current IP. Skipping remaining profiles. Tip: configure GOOGLE_SEARCH_API_KEY and GOOGLE_SEARCH_CX in Render.");
                    }
                }

                Thread.sleep(300); // Politeness delay between queries
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching LinkedIn jobs for study profile '{}': {}", studyName, e.getMessage());
            }
        }

        log.info("LinkedIn ingestion finished. Total offers retrieved: {}", results.size());
        return results;
    }

    /**
     * Modalidad 1: Búsqueda oficial mediante Google Custom Search API
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

    /**
     * Modalidad 2: Consulta directa a LinkedIn Jobs en caso de que aún no se hayan configurado
     * las claves de Google Custom Search en Render.
     */
    private boolean fetchViaLinkedInGuestApi(String studyName, String keywords, List<JobOffer> results, Set<String> seenUrls) {
        log.info("Fetching LinkedIn guest jobs for profile '{}' with keywords: '{}'", studyName, keywords);

        try {
            String uri = UriComponentsBuilder.fromHttpUrl(LINKEDIN_GUEST_API)
                    .queryParam("keywords", keywords)
                    .queryParam("location", "España")
                    .queryParam("start", 0)
                    .build()
                    .toUriString();

            String html = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9")
                    .retrieve()
                    .body(String.class);

            if (html == null || html.isBlank()) {
                return true;
            }

            // Parse job cards from LinkedIn public HTML response
            Pattern cardPattern = Pattern.compile(
                    "<a class=\"base-card__full-link[^\"]*\" href=\"([^\"]+)\"[^>]*>\\s*<span class=\"sr-only\">([^<]+)</span>.*?<h4 class=\"base-search-card__subtitle\">\\s*<a[^>]*>([^<]+)</a>.*?<span class=\"job-search-card__location\">\\s*([^<]+)</span>",
                    Pattern.DOTALL
            );

            Matcher matcher = cardPattern.matcher(html);
            int count = 0;
            while (matcher.find() && count < 15) {
                String rawLink = matcher.group(1).trim();
                String rawTitle = matcher.group(2).trim();
                String company = matcher.group(3).trim();
                String location = matcher.group(4).trim();

                // Clean LinkedIn redirect URLs
                String link = rawLink.contains("?") ? rawLink.substring(0, rawLink.indexOf("?")) : rawLink;

                if (seenUrls.contains(link)) {
                    continue;
                }
                seenUrls.add(link);
                count++;

                JobOffer offer = buildLinkedInJobOffer(link, rawTitle, company, location, studyName);
                if (offer != null) {
                    results.add(offer);
                }
            }
            return true;

        } catch (Exception e) {
            log.warn("LinkedIn guest endpoint response for profile '{}': {}", studyName, e.getMessage());
            return false;
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
