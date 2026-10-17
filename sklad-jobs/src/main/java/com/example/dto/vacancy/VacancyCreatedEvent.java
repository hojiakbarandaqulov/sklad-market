package com.example.dto.vacancy;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record VacancyCreatedEvent(
        String positionName,
        Long vacancyId,
        Long companyId,
        Long ownerId,
        Long categoryId,
        Long regionId,
        LocalDateTime createdDate,
        BigDecimal price
        ) {
}
