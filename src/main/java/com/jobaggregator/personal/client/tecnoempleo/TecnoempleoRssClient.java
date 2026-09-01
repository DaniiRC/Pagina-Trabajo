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

import java.net.URL;
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

    // Public RSS feeds for IT/tech jobs in Spain (category pages)
    private static final List<String> RSS_FEEDS = List.of(
        "https://www.tecnoempleo.com/rss/ofertas-empleo.xml",
        "https://www.tecnoempleo.com/rss/ofertas-empleo.xml?te=java,spring",
        "https://www.tecnoempleo.com/rss/ofertas-empleo.xml?te=linux,docker",
        "https://www.tecnoempleo.com/rss/ofertas-empleo.xml?te=redes,sistemas"
    );

    @Override
    public JobSource getSource() {
        return JobSource.TECNOEMPLEO;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("Tecnoempleo RSS client disabled.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (String feedUrl : RSS_FEEDS) {
            try {
                log.info("Fetching Tecnoempleo RSS: {}", feedUrl);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(new URL(feedUrl)));

                for (SyndEntry entry : feed.getEntries()) {
                    String link = entry.getLink();
                    if (link == null || link.isBlank() || seenUrls.contains(link)) continue;
                    seenUrls.add(link);

                    JobOffer offer = mapToJobOffer(entry);
                    if (offer != null) results.add(offer);
                }
            } catch (Exception e) {
                log.warn("Could not fetch Tecnoempleo RSS feed {}: {}", feedUrl, e.getMessage());
            }
        }

        log.info("Tecnoempleo ingestion finished. Total: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(SyndEntry entry) {
        String title = entry.getTitle();
        String link = entry.getLink();
        String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";

        String cleanDesc = technologyParserService.cleanHtmlDescription(description);
        String cleanFull = technologyParserService.cleanFullDescription(description);

        // Tecnoempleo puts location info in the title like: "Java Developer - Madrid"
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

        // Extract company name (often after last " - " in title)
        String cleanTitle = title;
        String companyName = "Empresa en Tecnoempleo";
        if (title != null && title.contains(" - ")) {
            String[] parts = title.split(" - ");
            cleanTitle = parts[0].trim();
            companyName = parts.length > 1 ? parts[parts.length - 1].trim() : companyName;
        }

        return JobOffer.builder()
                .externalId(link)
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
                .country(geo.country() != null ? geo.country() : "España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity())
                .build();
    }

    private String extractLocationFromTitle(String title) {
        if (title == null) return "España";
        // Try to find known Spanish cities/provinces at the end of title
        for (Map.Entry<String, List<String>> entry : SpanishGeographyService.COMMUNITIES_AND_PROVINCES.entrySet()) {
            for (String province : entry.getValue()) {
                if (title.toLowerCase().contains(province.toLowerCase())) {
                    return province + ", España";
                }
            }
        }
        if (title.toLowerCase().contains("remoto") || title.toLowerCase().contains("teletrabajo")) {
            return "Remoto, España";
        }
        return "España";
    }
}
