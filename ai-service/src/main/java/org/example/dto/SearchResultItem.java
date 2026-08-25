package org.example.dto;

/**
 * Compact projection of a vector-search hit, shaped like the read-tool projections (name, slug,
 * price, currency, region, category) plus the cosine similarity {@code score}. {@code slug} is what
 * the frontend turns into a {@code /product/<slug>} link.
 */
public record SearchResultItem(
        long productId,
        String slug,
        String name,
        Long categoryId,
        Long regionId,
        Double price,
        String currency,
        String imageUrl,
        double score) {

    /** Compatibility constructor for API/tests that predate optional result thumbnails. */
    public SearchResultItem(
            long productId,
            String slug,
            String name,
            Long categoryId,
            Long regionId,
            Double price,
            String currency,
            double score) {
        this(productId, slug, name, categoryId, regionId, price, currency, null, score);
    }
}
