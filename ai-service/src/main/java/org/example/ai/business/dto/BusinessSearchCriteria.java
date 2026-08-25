package org.example.ai.business.dto;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record BusinessSearchCriteria(
        String query,
        Set<BusinessResultType> types,
        Long categoryId,
        Long regionId,
        Double minPrice,
        Double maxPrice,
        String currency,
        int limit) {

    public BusinessSearchCriteria {
        query = query == null ? "" : query.trim();
        if (query.isEmpty()) throw new IllegalArgumentException("Query 'q' must not be empty");
        if (query.length() > 300) throw new IllegalArgumentException("Query 'q' must not exceed 300 characters");
        types = types == null || types.isEmpty()
                ? Set.copyOf(EnumSet.allOf(BusinessResultType.class))
                : Set.copyOf(types);
        if (minPrice != null && (!Double.isFinite(minPrice) || minPrice < 0)) {
            throw new IllegalArgumentException("minPrice must be finite and cannot be negative");
        }
        if (maxPrice != null && (!Double.isFinite(maxPrice) || maxPrice < 0)) {
            throw new IllegalArgumentException("maxPrice must be finite and cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("maxPrice must be greater than or equal to minPrice");
        }
        currency = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase(Locale.ROOT);
        if (currency != null && !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        if ((minPrice != null || maxPrice != null) && currency == null) {
            throw new IllegalArgumentException("currency is required when filtering product prices");
        }
        if ((minPrice != null || maxPrice != null || currency != null)
                && !types.contains(BusinessResultType.PRODUCT)) {
            throw new IllegalArgumentException(
                    "Currency and price filters apply only to individual product results, not company catalogs");
        }
        limit = Math.max(1, Math.min(limit, 30));
    }
}
