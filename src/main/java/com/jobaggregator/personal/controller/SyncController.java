package com.jobaggregator.personal.controller;

import com.jobaggregator.personal.dto.SyncResultDto;
import com.jobaggregator.personal.service.JobSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final JobSyncService jobSyncService;

    @PostMapping
    public ResponseEntity<SyncResultDto> triggerManualSync() {
        try {
            SyncResultDto result = jobSyncService.syncAll();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Fatal error during manual sync execution: ", e);
            SyncResultDto errorDto = SyncResultDto.builder()
                    .success(false)
                    .message("Error durante la sincronización: " + e.getMessage())
                    .totalFetched(0)
                    .newSaved(0)
                    .skippedDuplicates(0)
                    .fetchedBySource(Collections.emptyMap())
                    .sourceStatus(Collections.singletonMap("ERROR", e.getMessage()))
                    .durationMs(0)
                    .timestamp(LocalDateTime.now())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
        }
    }
}
