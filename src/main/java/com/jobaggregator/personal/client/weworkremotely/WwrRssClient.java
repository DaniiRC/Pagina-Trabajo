package com.jobaggregator.personal.client.weworkremotely;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.service.TechnologyParserService;
import com.rometools.rome.feed.synd.SyndCategory;
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
public class WwrRssClient implements JobIngestionClient {

    private final TechnologyParserService technologyParserService;

    @Value("${jobs.weworkremotely.enabled:true}")
    private boolean enabled;

    @Value("${jobs.weworkremotely.urls:https://weworkremotely.com/categories/remote-programming-jobs.rss,https://weworkremotely.com/categories/remote-devops-sysadmin-jobs.rss}")
    private List<String> rssUrls;

    @Override
    public JobSource getSource() {
        return JobSource.WEWORKREMOTELY;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("WeWorkRemotely RSS client is disabled in configuration.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        for (String urlStr : rssUrls) {
            try {
                log.info("Fetching RSS feed from WeWorkRemotely: {}", urlStr);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr.trim()))
                        .header("User-Agent", "PersonalJobAggregator/1.0 (Mozilla/5.0; RSS Feed Reader)")
                        .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<InputStream> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                    try (InputStream is = httpResponse.body()) {
                        SyndFeedInput input = new SyndFeedInput();
                        SyndFeed feed = input.build(new XmlReader(is));

                        for (SyndEntry entry : feed.getEntries()) {
                            String jobUrl = entry.getLink();
                            if (jobUrl == null || jobUrl.isBlank() || seenUrls.contains(jobUrl)) {
                                continue;
                            }
                            seenUrls.add(jobUrl);

                            JobOffer offer = mapEntryToJobOffer(entry);
                            if (offer != null) {
                                results.add(offer);
                            }
                        }
                    }
                } else {
                    log.warn("Failed to fetch RSS feed {}: HTTP status {}", urlStr, httpResponse.statusCode());
                }

            } catch (Exception e) {
                log.error("Error reading WeWorkRemotely RSS feed {}: {}", urlStr, e.getMessage());
            }
        }

        log.info("WeWorkRemotely ingestion finished. Total items fetched: {}", results.size());
        return results;
    }

    private JobOffer mapEntryToJobOffer(SyndEntry entry) {
        String rawTitle = entry.getTitle() != null ? entry.getTitle().trim() : "Oferta sin título";
        String company = "Empresa confidencial";
        String title = rawTitle;

        // WWR title format is often: "CompanyName: Job Title"
        if (rawTitle.contains(":")) {
            String[] parts = rawTitle.split(":", 2);
            company = parts[0].trim();
            title = parts[1].trim();
        }

        String rawDescription = entry.getDescription() != null ? entry.getDescription().getValue() : "";
        String cleanDescription = technologyParserService.cleanHtmlDescription(rawDescription);

        List<String> tags = new ArrayList<>();
        if (entry.getCategories() != null) {
            for (SyndCategory cat : entry.getCategories()) {
                if (cat.getName() != null && !cat.getName().isBlank()) {
                    tags.add(cat.getName().trim());
                }
            }
        }

        Set<String> techs = technologyParserService.extractTechnologies(rawTitle, cleanDescription, tags);

        LocalDateTime pubDate = LocalDateTime.now();
        if (entry.getPublishedDate() != null) {
            pubDate = LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault());
        }

        return JobOffer.builder()
                .externalId(entry.getUri() != null ? entry.getUri() : entry.getLink())
                .title(title)
                .companyName(company)
                .shortDescription(cleanDescription)
                .url(entry.getLink().trim())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .status(JobStatus.NUEVA)
                .source(JobSource.WEWORKREMOTELY)
                .isRemote(true)
                .location("Remote Worldwide")
                .build();
    }
}
