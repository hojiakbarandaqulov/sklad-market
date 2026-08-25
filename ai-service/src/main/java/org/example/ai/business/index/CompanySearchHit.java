package org.example.ai.business.index;

import java.util.List;

public record CompanySearchHit(
        long companyId,
        String slug,
        String name,
        String logoUrl,
        String verificationStatus,
        List<Long> categoryIds,
        List<Long> regionIds,
        int productCount,
        Double minPrice,
        Double maxPrice,
        double score) {

    /** Compatibility constructor for callers that do not provide optional company media. */
    public CompanySearchHit(
            long companyId,
            String slug,
            String name,
            String verificationStatus,
            List<Long> categoryIds,
            List<Long> regionIds,
            int productCount,
            Double minPrice,
            Double maxPrice,
            double score) {
        this(companyId, slug, name, null, verificationStatus, categoryIds, regionIds, productCount,
                minPrice, maxPrice, score);
    }
}
