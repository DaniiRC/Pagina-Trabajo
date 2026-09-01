package com.jobaggregator.personal.service;

import com.jobaggregator.personal.client.JobIngestionClient;
import com.jobaggregator.personal.dto.SyncResultDto;
import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSyncService {

    private final List<JobIngestionClient> ingestionClients;
    private final JobOfferRepository jobOfferRepository;

    /**
     * Tarea programada mediante Cron. Se ejecuta periódicamente según la configuración (por defecto cada 4 horas).
     */
    @Scheduled(cron = "${jobs.sync.cron:0 0 */4 * * *}")
    public void scheduledSync() {
        log.info("Starting scheduled job synchronization at {}", LocalDateTime.now());
        SyncResultDto result = syncAll();
        log.info("Scheduled sync completed. New jobs saved: {}, Total fetched: {}", result.getNewSaved(), result.getTotalFetched());
    }

    /**
     * Sincronización completa (manual o programada) con deduplicación y preservación de estado.
     */
    @Transactional
    public SyncResultDto syncAll() {
        int totalFetched = 0;
        int newSaved = 0;
        int skippedDuplicates = 0;
        Map<String, Integer> fetchedBySource = new HashMap<>();

        for (JobIngestionClient client : ingestionClients) {
            String sourceName = client.getSource().name();
            try {
                List<JobOffer> fetched = client.fetchJobs();
                int sourceCount = fetched != null ? fetched.size() : 0;
                fetchedBySource.put(sourceName, sourceCount);
                totalFetched += sourceCount;

                if (fetched != null) {
                    for (JobOffer offer : fetched) {
                        if (offer.getUrl() == null || offer.getUrl().isBlank()) {
                            continue;
                        }

                        // Comprobar si ya existe la oferta en la base de datos
                        Optional<JobOffer> existingOpt = jobOfferRepository.findByUrl(offer.getUrl().trim());
                        if (existingOpt.isPresent()) {
                            skippedDuplicates++;
                            // No sobreescribir si el usuario ya interactuó con la oferta
                        } else {
                            // Oferta nueva
                            offer.setStatus(JobStatus.NUEVA);
                            jobOfferRepository.save(offer);
                            newSaved++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to sync from source {}: {}", sourceName, e.getMessage(), e);
                fetchedBySource.put(sourceName, 0);
            }
        }

        return SyncResultDto.builder()
                .success(true)
                .message("Sincronización completada con éxito")
                .totalFetched(totalFetched)
                .newSaved(newSaved)
                .skippedDuplicates(skippedDuplicates)
                .fetchedBySource(fetchedBySource)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
