package com.jobaggregator.personal.client.infojobs;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.service.SpanishGeographyService;
import com.jobaggregator.personal.service.TechnologyParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class InfoJobsClient implements JobIngestionClient {

    private final RestClient restClient;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    @Value("${infojobs.client.id:}")
    private String clientId;

    @Value("${infojobs.client.secret:}")
    private String clientSecret;

    private static final String API_BASE = "https://api.infojobs.net/api/7/offer";
    private static final List<String> CATEGORIES = List.of(
        "informatica-telecomunicaciones",
        "ingenieros-tecnicos",
        "diseño-multimedia"
    );

    @Override
    public JobSource getSource() {
        return JobSource.INFOJOBS;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.info("InfoJobs client disabled: INFOJOBS_CLIENT_ID / INFOJOBS_CLIENT_SECRET not configured.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        for (String category : CATEGORIES) {
            try {
                String uri = API_BASE + "?category=" + category + "&maxResults=50&order=updated-desc";
                log.info("Fetching InfoJobs offers for category: {}", category);

                InfoJobsResponseDto response = restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(InfoJobsResponseDto.class);

                if (response == null || response.getItems() == null) continue;

                for (InfoJobsResponseDto.InfoJobsOfferItem item : response.getItems()) {
                    if (item.getLink() == null || seenUrls.contains(item.getLink())) continue;
                    seenUrls.add(item.getLink());

                    JobOffer offer = mapToJobOffer(item);
                    if (offer != null) results.add(offer);
                }

            } catch (Exception e) {
                log.error("Error fetching InfoJobs category {}: {}", category, e.getMessage());
            }
        }

        log.info("InfoJobs ingestion finished. Total: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(InfoJobsResponseDto.InfoJobsOfferItem item) {
        String companyName = item.getAuthor() != null ? item.getAuthor().getName() : "Empresa Confidencial";
        String rawLocation = buildLocation(item);

        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                rawLocation, item.getTitle(), null, false
        );

        String descText = item.getTitle(); // InfoJobs details endpoint would give full description
        Set<String> techs = technologyParserService.extractTechnologies(item.getTitle(), descText, null);
        Set<String> studies = inferStudyLevels(item);
        JobModality modality = inferModalityFromInfoJobs(item);

        LocalDateTime pubDate = parseDate(item.getPublished());

        return JobOffer.builder()
                .externalId(item.getId())
                .title(item.getTitle())
                .companyName(companyName)
                .shortDescription("Oferta en InfoJobs — " + (item.getCity() != null ? item.getCity() : rawLocation))
                .fullDescription("Consulta el detalle completo en InfoJobs. Ubicación: " + rawLocation + ". Tipo de contrato: " +
                        (item.getContractType() != null ? item.getContractType().getValue() : "No especificado"))
                .url(item.getLink())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.INFOJOBS)
                .modality(modality)
                .isRemote(modality == JobModality.REMOTO_100)
                .location(rawLocation)
                .continent(geo.continent())
                .country(geo.country())
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity() != null ? geo.provinceOrCity() : item.getCity())
                .build();
    }

    private String buildLocation(InfoJobsResponseDto.InfoJobsOfferItem item) {
        StringBuilder loc = new StringBuilder();
        if (item.getCity() != null && !item.getCity().isBlank()) loc.append(item.getCity());
        if (item.getProvince() != null && item.getProvince().getValue() != null) {
            if (!loc.isEmpty()) loc.append(", ");
            loc.append(item.getProvince().getValue());
        }
        loc.append(", España");
        return loc.toString();
    }

    private Set<String> inferStudyLevels(InfoJobsResponseDto.InfoJobsOfferItem item) {
        Set<String> studies = new HashSet<>();
        if (item.getRequirementMinStudies() != null) {
            String val = item.getRequirementMinStudies().getValue();
            if (val != null) {
                String v = val.toLowerCase();
                if (v.contains("grado") || v.contains("licenciatura")) studies.add("GRADO_INFORMATICA");
                if (v.contains("ingenier")) studies.add("INGENIERIA");
                if (v.contains("fp") || v.contains("ciclo") || v.contains("superior")) {
                    studies.add("DAM"); studies.add("DAW"); studies.add("ASIR");
                }
                if (v.contains("bachiller") || v.contains("sin titulaci")) studies.add("SIN_ESTUDIOS");
                if (v.contains("bootcamp")) studies.add("BOOTCAMP");
            }
        }
        return studies.isEmpty() ? Set.of("DAM", "DAW", "ASIR") : studies;
    }

    private JobModality inferModalityFromInfoJobs(InfoJobsResponseDto.InfoJobsOfferItem item) {
        if (item.getTeleworking() != null && item.getTeleworking().getValue() != null) {
            String tw = item.getTeleworking().getValue().toLowerCase();
            if (tw.contains("100") || tw.contains("completo") || tw.contains("teletrabajo")) return JobModality.REMOTO_100;
            if (tw.contains("h") && tw.contains("brido") || tw.contains("hybrid") || tw.contains("parcial")) return JobModality.HIBRIDO;
        }
        return JobModality.PRESENCIAL;
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
