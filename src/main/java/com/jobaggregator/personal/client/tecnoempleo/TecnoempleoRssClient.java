package com.jobaggregator.personal.client.tecnoempleo;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.TechnologyParserService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TecnoempleoRssClient implements JobIngestionClient {

    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    @Value("${jobs.tecnoempleo.enabled:true}")
    private boolean enabled;

    @Value("${jobs.tecnoempleo.url:https://www.tecnoempleo.com/rss.xml}")
    private String primaryRssUrl;

    @Override
    public JobSource getSource() {
        return JobSource.TECNOEMPLEO;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Tecnoempleo RSS client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        List<String> urlsToTry = List.of(
                primaryRssUrl,
                "https://www.tecnoempleo.com/rss/ofertas-empleo.xml"
        );

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        for (String feedUrl : urlsToTry) {
            try {
                log.info("Attempting to fetch Tecnoempleo RSS from: {}", feedUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(feedUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try (InputStream is = response.body()) {
                        SyndFeedInput input = new SyndFeedInput();
                        SyndFeed feed = input.build(new XmlReader(is));

                        for (SyndEntry entry : feed.getEntries()) {
                            String link = entry.getLink();
                            if (link == null || link.isBlank() || seenUrls.contains(link.trim())) {
                                continue;
                            }
                            seenUrls.add(link.trim());

                            JobOffer offer = mapToJobOffer(entry);
                            if (offer != null) {
                                results.add(offer);
                            }
                        }
                    }
                    // If successfully fetched first feed, don't need redundant fallback
                    if (!results.isEmpty()) {
                        break;
                    }
                } else {
                    log.error("Tecnoempleo RSS returned HTTP status {}: {}", response.statusCode(), feedUrl);
                }

            } catch (Exception e) {
                log.error("Could not parse Tecnoempleo RSS feed [{}]: {}", feedUrl, e.getMessage());
            }
        }

        log.info("Tecnoempleo ingestion finished. Total offers retrieved: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(SyndEntry entry) {
        String title = entry.getTitle() != null ? entry.getTitle().trim() : "Oferta Técnica";
        String link = entry.getLink() != null ? entry.getLink().trim() : "";
        String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";

        String cleanDesc = technologyParserService.cleanHtmlDescription(description);
        String cleanFull = technologyParserService.cleanFullDescription(description);

        String rawLocation = extractLocationFromTitle(title);
        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                rawLocation, title, cleanDesc, null
        );

        Set<String> techs = technologyParserService.extractTechnologies(title, cleanFull, null);
        Set<String> studies = technologyParserService.extractStudyLevels(title, cleanFull, techs);
        JobModality modality = technologyParserService.inferModality(null, rawLocation, cleanFull);

        LocalDateTime pubDate = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().atZone(ZoneId.of("Europe/Madrid")).toLocalDateTime()
                : LocalDateTime.now();

        String cleanTitle = title;
        String companyName = "Empresa en Tecnoempleo";
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ");
            cleanTitle = parts[0].trim();
            companyName = parts.length > 1 ? parts[parts.length - 1].trim() : companyName;
        }

        return JobOffer.builder()
                .externalId("tecnoempleo-" + (link.isBlank() ? UUID.randomUUID().toString() : link))
                .title(cleanTitle)
                .companyName(companyName)
                .shortDescription(cleanDesc)
                .fullDescription(cleanFull)
                .url(link)
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.TECNOEMPLEO)
                .modality(modality)
                .isRemote(modality == JobModality.REMOTO_100)
                .location(rawLocation != null ? rawLocation : "España")
                .continent(geo.continent() != null ? geo.continent() : "Europa")
                .country("España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .build();
    }

    private String extractLocationFromTitle(String title) {
        if (title == null) return "España";
        for (Map.Entry<String, List<String>> entry : SpanishGeographyService.COMMUNITIES_AND_PROVINCES.entrySet()) {
            for (String province : entry.getValue()) {
                if (title.toLowerCase().contains(province.toLowerCase())) {
                    return province + ", España";
                }
            }
        }
        if (title.toLowerCase().contains("remoto") || title.toLowerCase().contains("teletrabajo")) {
            return "España · Remoto";
        }
        return "España";
    }
}
