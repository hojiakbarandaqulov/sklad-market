package org.example.ai.business.dto;

public record SupplierRecommendationRequest(
        String need,
        String categorySlug,
        Long regionId,
        Integer limit) {
}
