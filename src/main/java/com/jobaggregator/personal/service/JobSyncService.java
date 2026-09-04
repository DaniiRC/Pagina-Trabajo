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
     * Enriquecer datos existentes en la BD, eliminar registros no técnicos y corregir geografía.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void enrichExistingRecords() {
        try {
            List<JobOffer> allOffers = jobOfferRepository.findAll();
            int updated = 0;
            int deletedNonTech = 0;

            for (JobOffer offer : allOffers) {
                // Delete existing non-tech jobs (e.g. Spa Manager, etc.)
                if (!technologyParserService.isTechJob(offer.getTitle(), offer.getFullDescription(), null, offer.getRequiredTechnologies())) {
                    jobOfferRepository.delete(offer);
                    deletedNonTech++;
                    continue;
                }

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

                // Re-evaluate geography with strict SpanishGeographyService
                SpanishGeographyService.GeoResult geo = spanishGeographyService.inferGeography(
                        offer.getLocation(), offer.getTitle(), offer.getFullDescription(), offer.getIsRemote()
                );
                if (!Objects.equals(offer.getContinent(), geo.continent()) ||
                    !Objects.equals(offer.getCountry(), geo.country()) ||
                    !Objects.equals(offer.getAutonomousCommunity(), geo.autonomousCommunity()) ||
                    !Objects.equals(offer.getProvinceOrCity(), geo.provinceOrCity())) {
                    offer.setContinent(geo.continent());
                    offer.setCountry(geo.country());
                    offer.setAutonomousCommunity(geo.autonomousCommunity());
                    offer.setProvinceOrCity(geo.provinceOrCity());
                    modified = true;
                }

                if (modified) {
                    sanitizeOfferFields(offer);
                    jobOfferRepository.save(offer);
                    updated++;
                }
            }

            if (deletedNonTech > 0 || updated > 0) {
                log.info("Startup enrichment completed: {} non-tech offers purged, {} offers updated with geo/modality metadata.", deletedNonTech, updated);
            }
        } catch (Exception e) {
            log.warn("Non-fatal: could not complete startup enrichment: {}", e.getMessage());
        }
    }

    /**
     * Sincronización completa con ejecución paralela de clientes para máxima velocidad y tolerancia a fallos.
     */
    public SyncResultDto syncAll() {
        long startTime = System.currentTimeMillis();
        int totalFetched = 0;
        int newSaved = 0;
        int skippedDuplicates = 0;

        Map<String, Integer> fetchedBySource = new ConcurrentHashMap<>();
        Map<String, String> sourceStatus = new ConcurrentHashMap<>();

        // Fetch from all ingestion clients in parallel
        List<CompletableFuture<List<JobOffer>>> futures = new ArrayList<>();

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
                        sourceStatus.put(name, "0 ofertas o cliente no configurado");
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
        }

        // Wait for all fetches to complete with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(12, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Sync parallel fetch completed with timeout/interruption: {}", e.getMessage());
        }

        // Aggregate and persist results safely
        for (CompletableFuture<List<JobOffer>> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    List<JobOffer> jobs = future.get();
                    totalFetched += jobs.size();

                    for (JobOffer offer : jobs) {
                        try {
                            if (offer == null || offer.getUrl() == null || offer.getUrl().isBlank()) {
                                continue;
                            }

                            // Extra verification: Must be a tech job
                            if (!technologyParserService.isTechJob(offer.getTitle(), offer.getFullDescription(), null, offer.getRequiredTechnologies())) {
                                continue;
                            }

                            // Anti-senior filter: skip offers clearly requiring senior/lead/architect profiles
                            if (!technologyParserService.isJuniorFriendly(offer.getTitle(), offer.getFullDescription())) {
                                log.debug("Skipping senior-only offer: {}", offer.getTitle());
                                continue;
                            }

                            String cleanUrl = offer.getUrl().trim();
                            Optional<JobOffer> existingOpt = jobOfferRepository.findByUrl(cleanUrl);
                            if (existingOpt.isPresent()) {
                                skippedDuplicates++;
                            } else {
                                sanitizeOfferFields(offer);
                                if (offer.getStatus() == null) {
                                    offer.setStatus(JobStatus.NUEVA);
                                }
                                jobOfferRepository.save(offer);
                                newSaved++;
                            }
                        } catch (Exception ex) {
                            log.warn("Skipping offer save due to database constraint: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing future job batch: {}", e.getMessage());
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

    private void sanitizeOfferFields(JobOffer offer) {
        if (offer.getTitle() != null && offer.getTitle().length() > 500) {
            offer.setTitle(offer.getTitle().substring(0, 497) + "...");
        }
        if (offer.getCompanyName() != null && offer.getCompanyName().length() > 255) {
            offer.setCompanyName(offer.getCompanyName().substring(0, 252) + "...");
        } else if (offer.getCompanyName() == null || offer.getCompanyName().isBlank()) {
            offer.setCompanyName("Empresa Confidencial");
        }
        if (offer.getUrl() != null && offer.getUrl().length() > 1000) {
            offer.setUrl(offer.getUrl().substring(0, 1000));
        }
        if (offer.getLocation() != null && offer.getLocation().length() > 255) {
            offer.setLocation(offer.getLocation().substring(0, 255));
        }
        if (offer.getContinent() != null && offer.getContinent().length() > 100) {
            offer.setContinent(offer.getContinent().substring(0, 100));
        }
        if (offer.getCountry() != null && offer.getCountry().length() > 100) {
            offer.setCountry(offer.getCountry().substring(0, 100));
        }
        if (offer.getAutonomousCommunity() != null && offer.getAutonomousCommunity().length() > 100) {
            offer.setAutonomousCommunity(offer.getAutonomousCommunity().substring(0, 100));
        }
        if (offer.getProvinceOrCity() != null && offer.getProvinceOrCity().length() > 150) {
            offer.setProvinceOrCity(offer.getProvinceOrCity().substring(0, 150));
        }
        if (offer.getExternalId() != null && offer.getExternalId().length() > 255) {
            offer.setExternalId(offer.getExternalId().substring(0, 255));
        }
    }
}
