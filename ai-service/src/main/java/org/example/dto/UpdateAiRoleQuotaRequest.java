package org.example.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateAiRoleQuotaRequest(
        @NotNull @Min(0) @Max(1_000_000) Integer hourlyRequestLimit,
        @NotNull @Min(0) @Max(1_000_000) Integer dailyRequestLimit) {
}
