package org.example.dto;

import java.time.Instant;

public record AiRoleQuotaDto(
        String roleName,
        int hourlyRequestLimit,
        int dailyRequestLimit,
        Instant updatedAt,
        String updatedBy) {
}
