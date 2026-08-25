package org.example.ai.intent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seller-visible projection. Owner/contact columns are excluded. {@code needText}, category, and
 * region are user-authored publication fields and therefore cannot be described as anonymous.
 */
public record BuyingIntentMatchResponse(
        UUID intentId,
        String category,
        String region,
        String needText,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String currency,
        Instant expiresAt,
        int matchScore,
        List<String> reasons,
        boolean contactAvailable,
        String contactAccess,
        boolean automaticOutreachAllowed) {
}
