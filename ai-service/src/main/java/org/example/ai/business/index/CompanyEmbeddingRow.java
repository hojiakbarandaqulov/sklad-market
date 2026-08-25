package org.example.ai.business.index;

import java.util.List;

public record CompanyEmbeddingRow(
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
        String contentHash,
        float[] embedding) {

    /** Compatibility constructor for rows created before company logos were indexed. */
    public CompanyEmbeddingRow(
            long companyId,
            String slug,
            String name,
            String verificationStatus,
            List<Long> categoryIds,
            List<Long> regionIds,
            int productCount,
            Double minPrice,
            Double maxPrice,
            String contentHash,
            float[] embedding) {
        this(companyId, slug, name, null, verificationStatus, categoryIds, regionIds, productCount,
                minPrice, maxPrice, contentHash, embedding);
    }
}
