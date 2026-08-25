package org.example.ai.intent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BuyingIntentResponse(
        UUID id,
        String status,
        String category,
        String region,
        String needText,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String currency,
        Instant expiresAt,
        Instant publishedAt,
        Instant publicationConsentAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean contactAvailable,
        String contactAccess,
        String visibility,
        String publicationDisclosure,
        boolean automaticOutreachAllowed) {
}
