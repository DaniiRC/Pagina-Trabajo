package com.jobaggregator.personal.controller;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.dto.JobStatsDto;
import com.jobaggregator.personal.dto.UpdateJobStatusDto;
import com.jobaggregator.personal.model.JobModality;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.service.JobOfferService;
import com.jobaggregator.personal.service.SpanishGeographyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;
    private final SpanishGeographyService spanishGeographyService;

    @GetMapping
    public ResponseEntity<Page<JobOfferResponseDto>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tech,
            @RequestParam(required = false) Boolean isRemote,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String community,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) JobModality modality,
            @RequestParam(required = false) List<String> studies,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<JobOfferResponseDto> offers = jobOfferService.getOffers(
                status, keyword, tech, isRemote, location,
                country, community, province, modality, studies, page, size
        );
        return ResponseEntity.ok(offers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobOfferResponseDto> getJobById(@PathVariable Long id) {
        return ResponseEntity.ok(jobOfferService.getOfferById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobOfferResponseDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobStatusDto updateDto
    ) {
        return ResponseEntity.ok(jobOfferService.updateStatus(id, updateDto.getStatus()));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<JobOfferResponseDto> markAsViewed(@PathVariable Long id) {
        return ResponseEntity.ok(jobOfferService.markAsViewedIfNew(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStatsDto> getStats() {
        return ResponseEntity.ok(jobOfferService.getStats());
    }

    /** Returns the Spanish geography tree for frontend cascading selects */
    @GetMapping("/geo/spain")
    public ResponseEntity<Map<String, List<String>>> getSpanishGeography() {
        return ResponseEntity.ok(spanishGeographyService.getSpanishGeographyTree());
    }
}
