package com.jobaggregator.personal.controller;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.dto.JobStatsDto;
import com.jobaggregator.personal.dto.UpdateJobStatusDto;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.service.JobOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;

    @GetMapping
    public ResponseEntity<Page<JobOfferResponseDto>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tech,
            @RequestParam(required = false) Boolean isRemote,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<JobOfferResponseDto> offers = jobOfferService.getOffers(status, keyword, tech, isRemote, location, page, size);
        return ResponseEntity.ok(offers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobOfferResponseDto> getJobById(@PathVariable Long id) {
        JobOfferResponseDto offer = jobOfferService.getOfferById(id);
        return ResponseEntity.ok(offer);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobOfferResponseDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobStatusDto updateDto
    ) {
        JobOfferResponseDto updated = jobOfferService.updateStatus(id, updateDto.getStatus());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<JobOfferResponseDto> markAsViewed(@PathVariable Long id) {
        JobOfferResponseDto updated = jobOfferService.markAsViewedIfNew(id);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStatsDto> getStats() {
        return ResponseEntity.ok(jobOfferService.getStats());
    }
}
