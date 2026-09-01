package com.jobaggregator.personal.dto;

import com.jobaggregator.personal.model.JobStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobStatusDto {
    @NotNull(message = "El estado no puede ser nulo")
    private JobStatus status;
}
