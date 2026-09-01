package com.jobaggregator.personal.controller;

import com.jobaggregator.personal.dto.SyncResultDto;
import com.jobaggregator.personal.service.JobSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final JobSyncService jobSyncService;

    @PostMapping
    public ResponseEntity<SyncResultDto> triggerManualSync() {
        SyncResultDto result = jobSyncService.syncAll();
        return ResponseEntity.ok(result);
    }
}
