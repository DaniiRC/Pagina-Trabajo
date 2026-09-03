package com.jobaggregator.personal.client.infojobs;

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

import java.nio.charset.StandardCharsets;
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
    private final StudyKeywordMapperService studyKeywordMapperService;

    @Value("${infojobs.client.id:${INFOJOBS_CLIENT_ID:}}")
    private String clientId;

    @Value("${infojobs.client.secret:${INFOJOBS_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${jobs.infojobs.enabled:true}")
    private boolean enabled;

    private static final String API_URL = "https://api.infojobs.net/api/1/offer";

    @Override
    public JobSource getSource() {
        return JobSource.INFOJOBS;
    }

    @Override
    public List<JobOffer> fetchJobs() {
        if (!enabled) {
            log.info("InfoJobs client disabled in configuration.");
            return Collections.emptyList();
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.info("InfoJobs client skipped: INFOJOBS_CLIENT_ID and INFOJOBS_CLIENT_SECRET are not configured.");
            return Collections.emptyList();
        }

        List<JobOffer> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        String basicAuthCredentials = Base64.getEncoder().encodeToString(
                (clientId.trim() + ":" + clientSecret.trim()).getBytes(StandardCharsets.UTF_8)
        );

        // Keywords mapped from study profiles (DAM, DAW, SMR, ASIR...)
        Map<String, String> studyQueries = studyKeywordMapperService.getAllStudyMappings();

        for (Map.Entry<String, String> entry : studyQueries.entrySet()) {
            String studyProfile = entry.getKey();
            String searchQuery = entry.getValue();

            try {
                String uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                        .queryParam("q", searchQuery)
                        .queryParam("maxResults", 25)
                        .queryParam("order", "updated-desc")
                        .build()
                        .toUriString();

                log.info("Fetching InfoJobs offers for profile '{}' with query: {}", studyProfile, searchQuery);

                InfoJobsResponseDto response = restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthCredentials)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(InfoJobsResponseDto.class);

                if (response == null || response.getItems() == null) {
                    continue;
                }

                for (InfoJobsResponseDto.InfoJobsOfferItem item : response.getItems()) {
                    if (item.getLink() == null || item.getLink().isBlank() || seenUrls.contains(item.getLink().trim())) {
                        continue;
                    }
                    seenUrls.add(item.getLink().trim());

                    JobOffer offer = mapToJobOffer(item, studyProfile);
                    if (offer != null) {
                        results.add(offer);
                    }
                }

                // Respect rate limiting
                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching InfoJobs offers for profile '{}': {}", studyProfile, e.getMessage());
            }
        }

        log.info("InfoJobs ingestion finished. Total offers retrieved: {}", results.size());
        return results;
    }

    private JobOffer mapToJobOffer(InfoJobsResponseDto.InfoJobsOfferItem item, String inferredStudyProfile) {
        String companyName = item.getAuthor() != null && item.getAuthor().getName() != null
                ? item.getAuthor().getName().trim()
                : "Empresa Confidencial";

        String rawLocation = buildLocation(item);

        SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                rawLocation, item.getTitle(), null, false
        );

        String descText = item.getTitle();
        Set<String> techs = technologyParserService.extractTechnologies(item.getTitle(), descText, null);
        Set<String> studies = inferStudyLevels(item, inferredStudyProfile);
        JobModality modality = inferModalityFromInfoJobs(item);

        LocalDateTime pubDate = parseDate(item.getPublished() != null ? item.getPublished() : item.getUpdated());

        String shortDesc = "Oferta en InfoJobs — " + (item.getCity() != null ? item.getCity() : rawLocation);
        String fullDesc = "Puesto: " + item.getTitle() + "\n"
                + "Empresa: " + companyName + "\n"
                + "Ubicación: " + rawLocation + "\n"
                + "Contrato: " + (item.getContractType() != null ? item.getContractType().getValue() : "No especificado") + "\n"
                + "Jornada: " + (item.getWorkDay() != null ? item.getWorkDay().getValue() : "No especificada") + "\n"
                + "Experiencia mínima: " + (item.getExperienceMin() != null ? item.getExperienceMin().getValue() : "No indicada") + "\n"
                + "Estudios mínimos: " + (item.getRequirementMinStudies() != null ? item.getRequirementMinStudies().getValue() : "No indicados") + "\n"
                + "Salario: " + (item.getSalaryDescription() != null ? item.getSalaryDescription().getValue() : "A convenir");

        return JobOffer.builder()
                .externalId("infojobs-" + (item.getId() != null ? item.getId() : UUID.randomUUID().toString()))
                .title(item.getTitle() != null ? item.getTitle().trim() : "Oferta en InfoJobs")
                .companyName(companyName)
                .shortDescription(shortDesc)
                .fullDescription(fullDesc)
                .url(item.getLink().trim())
                .publishedDate(pubDate)
                .requiredTechnologies(techs)
                .studyLevels(studies)
                .status(JobStatus.NUEVA)
                .source(JobSource.INFOJOBS)
                .modality(modality)
                .isRemote(modality == JobModality.REMOTO_100)
                .location(rawLocation)
                .continent(geo.continent() != null ? geo.continent() : "Europa")
                .country(geo.country() != null ? geo.country() : "España")
                .autonomousCommunity(geo.autonomousCommunity())
                .provinceOrCity(geo.provinceOrCity() != null ? geo.provinceOrCity() : item.getCity())
                .build();
    }

    private String buildLocation(InfoJobsResponseDto.InfoJobsOfferItem item) {
        StringBuilder loc = new StringBuilder();
        if (item.getCity() != null && !item.getCity().isBlank()) {
            loc.append(item.getCity().trim());
        }
        if (item.getProvince() != null && item.getProvince().getValue() != null && !item.getProvince().getValue().isBlank()) {
            if (!loc.isEmpty()) loc.append(", ");
            loc.append(item.getProvince().getValue().trim());
        }
        if (loc.isEmpty()) {
            loc.append("España");
        } else {
            loc.append(", España");
        }
        return loc.toString();
    }

    private Set<String> inferStudyLevels(InfoJobsResponseDto.InfoJobsOfferItem item, String defaultStudyProfile) {
        Set<String> studies = new HashSet<>();
        if (defaultStudyProfile != null && !defaultStudyProfile.isBlank()) {
            studies.add(defaultStudyProfile.toUpperCase());
        }

        if (item.getRequirementMinStudies() != null && item.getRequirementMinStudies().getValue() != null) {
            String val = item.getRequirementMinStudies().getValue().toLowerCase();
            if (val.contains("grado") || val.contains("licenciatura")) {
                studies.add("GRADO_INFORMATICA");
            }
            if (val.contains("ingenier")) {
                studies.add("INGENIERIA");
            }
            if (val.contains("fp") || val.contains("ciclo") || val.contains("superior") || val.contains("formacion profesional")) {
                studies.add("DAM");
                studies.add("DAW");
                studies.add("ASIR");
            }
            if (val.contains("medio")) {
                studies.add("SMR");
            }
        }

        return studies.isEmpty() ? Set.of("DAM", "DAW", "ASIR") : studies;
    }

    private JobModality inferModalityFromInfoJobs(InfoJobsResponseDto.InfoJobsOfferItem item) {
        if (item.getTeleworking() != null && item.getTeleworking().getValue() != null) {
            String tw = item.getTeleworking().getValue().toLowerCase();
            if (tw.contains("100") || tw.contains("completo") || tw.contains("solo teletrabajo") || tw.contains("remoto")) {
                return JobModality.REMOTO_100;
            }
            if (tw.contains("hibrido") || tw.contains("híbrido") || tw.contains("hybrid") || tw.contains("parcial")) {
                return JobModality.HIBRIDO;
            }
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
