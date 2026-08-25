package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * The rich list item from {@code GET /api/v1/products/all} (product-service's {@code ProductResponse},
 * camelCase wire shape — verified in source on {@code main} and live on skladmarket.uz, 2026-07-11).
 * Unlike {@code /api/v1/catalog}, {@code /all} applies NO moderation/active filter (PLAN.md §7 item
 * 12), so it exposes {@code status} + {@code isActive} which the indexer uses to exclude anything not
 * publicly visible ({@code status == "APPROVED" && isActive == true}) — mirroring product-service's
 * own catalog-visibility predicate ({@code moderationStatus=APPROVED AND isActive AND deletedAt IS
 * NULL}; a soft-deleted product keeps its APPROVED status but has {@code isActive=false}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteIndexProductDto(
        Long id,
        String name,
        String slug,
        String shortDescription,
        String description,
        Double price,
        String currency,
        Long regionId,
        Long categoryId,
        String status,
        Boolean isActive,
        Map<String, Object> attributes,
        List<RemoteProductImageDto> images) {

    /** Compatibility constructor for existing callers that do not need the optional image list. */
    public RemoteIndexProductDto(
            Long id,
            String name,
            String slug,
            String shortDescription,
            String description,
            Double price,
            String currency,
            Long regionId,
            Long categoryId,
            String status,
            Boolean isActive,
            Map<String, Object> attributes) {
        this(id, name, slug, shortDescription, description, price, currency, regionId, categoryId,
                status, isActive, attributes, null);
    }

    public boolean isPubliclyVisible() {
        return "APPROVED".equals(status) && Boolean.TRUE.equals(isActive);
    }

    public String primaryImageUrl() {
        return RemoteProductImageDto.primaryCardUrl(images);
    }
}
