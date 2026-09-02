package com.jobaggregator.personal.service;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.dto.SyncResultDto;
import com.jobaggregator.personal.model.*;
import com.jobaggregator.personal.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSyncService {

    private final List<JobIngestionClient> ingestionClients;
    private final JobOfferRepository jobOfferRepository;
    private final TechnologyParserService technologyParserService;
    private final SpanishGeographyService spanishGeographyService;

    private static final ExecutorService SYNC_EXECUTOR = Executors.newFixedThreadPool(8);

    /**
     * Tarea programada periódica mediante Cron (por defecto cada 4 horas).
     */
    @Scheduled(cron = "${jobs.sync.cron:0 0 */4 * * *}")
    public void scheduledSync() {
        log.info("Starting scheduled job synchronization at {}", LocalDateTime.now());
        SyncResultDto result = syncAll();
        log.info("Scheduled sync completed. New saved: {}, Total: {}, Duration: {}ms",
                result.getNewSaved(), result.getTotalFetched(), result.getDurationMs());
    }

    /**
     * Enriquecer datos existentes en la BD que puedan no tener modality, studyLevels o geografía.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void enrichExistingRecords() {
        try {
            List<JobOffer> allOffers = jobOfferRepository.findAll();
            int updated = 0;

            for (JobOffer offer : allOffers) {
                boolean modified = false;

                // Enrich technologies if empty
                if (offer.getRequiredTechnologies() == null || offer.getRequiredTechnologies().isEmpty()) {
                    Set<String> techs = technologyParserService.extractTechnologies(
                            offer.getTitle(), offer.getFullDescription(), null
                    );
                    offer.setRequiredTechnologies(techs);
                    modified = true;
                }

                // Enrich studyLevels if empty
                if (offer.getStudyLevels() == null || offer.getStudyLevels().isEmpty()) {
                    Set<String> studies = technologyParserService.extractStudyLevels(
                            offer.getTitle(), offer.getFullDescription(), offer.getRequiredTechnologies()
                    );
                    offer.setStudyLevels(studies);
                    modified = true;
                }

                // Enrich modality if null
                if (offer.getModality() == null) {
                    JobModality modality = technologyParserService.inferModality(
                            offer.getIsRemote(), offer.getLocation(), offer.getFullDescription()
                    );
                    offer.setModality(modality);
                    modified = true;
                }

                // Enrich geography if country or continent is null
                if (offer.getCountry() == null || offer.getContinent() == null) {
                    SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                            offer.getLocation(), offer.getTitle(), offer.getFullDescription(), offer.getIsRemote()
                    );
                    if (offer.getContinent() == null) offer.setContinent(geo.continent());
                    if (offer.getCountry() == null) offer.setCountry(geo.country());
                    if (offer.getAutonomousCommunity() == null) offer.setAutonomousCommunity(geo.autonomousCommunity());
                    if (offer.getProvinceOrCity() == null) offer.setProvinceOrCity(geo.provinceOrCity());
                    modified = true;
                }

                if (modified) {
                    jobOfferRepository.save(offer);
                    updated++;
                }
            }

            if (updated > 0) {
                log.info("Startup enrichment completed: {} existing job offers updated with geo/modality/studies metadata.", updated);
            }
        } catch (Exception e) {
            log.warn("Non-fatal: could not complete startup enrichment: {}", e.getMessage());
        }
    }

    /**
     * Sincronización completa con ejecución paralela de clientes para máxima velocidad.
     */
    @Transactional
    public SyncResultDto syncAll() {
        long startTime = System.currentTimeMillis();
        int totalFetched = 0;
        int newSaved = 0;
        int skippedDuplicates = 0;

        Map<String, Integer> fetchedBySource = new ConcurrentHashMap<>();
        Map<String, String> sourceStatus = new ConcurrentHashMap<>();

        // Fetch from all ingestion clients in parallel
        List<CompletableFuture<List<JobOffer>>> futures = new ArrayList<>();
        Map<CompletableFuture<List<JobOffer>>, JobIngestionClient> clientMap = new HashMap<>();

        for (JobIngestionClient client : ingestionClients) {
            CompletableFuture<List<JobOffer>> future = CompletableFuture.supplyAsync(() -> {
                String name = client.getSource().name();
                try {
                    List<JobOffer> jobs = client.fetchJobs();
                    int count = jobs != null ? jobs.size() : 0;
                    fetchedBySource.put(name, count);
                    if (count > 0) {
                        sourceStatus.put(name, count + " ofertas encontradas");
                    } else {
                        sourceStatus.put(name, "Sin nuevas ofertas o cliente no configurado");
                    }
                    return jobs != null ? jobs : Collections.emptyList();
                } catch (Exception e) {
                    log.error("Error fetching from client {}: {}", name, e.getMessage());
                    fetchedBySource.put(name, 0);
                    sourceStatus.put(name, "Error: " + e.getMessage());
                    return Collections.<JobOffer>emptyList();
                }
            }, SYNC_EXECUTOR);

            futures.add(future);
            clientMap.put(future, client);
        }

        // Wait for all fetches to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Sync parallel fetch completed with timeout/interruption: {}", e.getMessage());
        }

        // Aggregate and persist results
        for (CompletableFuture<List<JobOffer>> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    List<JobOffer> jobs = future.get();
                    totalFetched += jobs.size();

                    for (JobOffer offer : jobs) {
                        if (offer.getUrl() == null || offer.getUrl().isBlank()) continue;

                        Optional<JobOffer> existingOpt = jobOfferRepository.findByUrl(offer.getUrl().trim());
                        if (existingOpt.isPresent()) {
                            skippedDuplicates++;
                        } else {
                            offer.setStatus(JobStatus.NUEVA);
                            jobOfferRepository.save(offer);
                            newSaved++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error saving job batch: {}", e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;

        return SyncResultDto.builder()
                .success(true)
                .message("Sincronización finalizada correctamente en " + (durationMs / 1000.0) + "s")
                .totalFetched(totalFetched)
                .newSaved(newSaved)
                .skippedDuplicates(skippedDuplicates)
                .fetchedBySource(fetchedBySource)
                .sourceStatus(sourceStatus)
                .durationMs(durationMs)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
