package com.jobaggregator.personal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResultDto {
    private boolean success;
    private String message;
    private int totalFetched;
    private int newSaved;
    private int skippedDuplicates;
    private Map<String, Integer> fetchedBySource;
    private LocalDateTime timestamp;
}
