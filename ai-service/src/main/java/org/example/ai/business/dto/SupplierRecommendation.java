package org.example.ai.business.dto;

import java.util.List;

public record SupplierRecommendation(
        Long companyId,
        String slug,
        String name,
        String logoUrl,
        String verificationStatus,
        List<Long> categoryIds,
        List<Long> regionIds,
        int productCount,
        Double minPrice,
        Double maxPrice,
        double relevance,
        List<String> reasons,
        BusinessContactStatus contactStatus,
        BusinessContact contact) {

    /** Compatibility constructor for consumers created before supplier logos were exposed. */
    public SupplierRecommendation(
            Long companyId,
            String slug,
            String name,
            String verificationStatus,
            List<Long> categoryIds,
            List<Long> regionIds,
            int productCount,
            Double minPrice,
            Double maxPrice,
            double relevance,
            List<String> reasons,
            BusinessContactStatus contactStatus,
            BusinessContact contact) {
        this(companyId, slug, name, null, verificationStatus, categoryIds, regionIds, productCount,
                minPrice, maxPrice, relevance, reasons, contactStatus, contact);
    }
}
