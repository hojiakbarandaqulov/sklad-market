package org.example.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
