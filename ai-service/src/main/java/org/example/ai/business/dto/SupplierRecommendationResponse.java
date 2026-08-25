package org.example.ai.business.dto;

import java.util.List;

public record SupplierRecommendationResponse(
        String personalizationSource,
        int count,
        List<SupplierRecommendation> items,
        String scoreMeaning,
        String disclaimer,
        BusinessIndexFreshness indexFreshness) {
}
