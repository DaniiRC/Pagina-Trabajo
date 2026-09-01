package com.jobaggregator.personal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatsDto {
    private long total;
    private long nuevas;
    private long vistas;
    private long aplicadas;
    private long descartadas;
    private List<String> availableTechnologies;
}
