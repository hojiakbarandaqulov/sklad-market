package org.example.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ProductCreatedEvent(
        Long productId,
        Long companyId,
        Long categoryId,
        Long sellerId,
        String name,
        String phone,
        String slug,
        BigDecimal price,
        String currency,
        String moderationStatus,
        LocalDateTime createdAt
) {
}
