package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Compact view of product-service's {@code ProductResponse} (camelCase wire shape), as returned by {@code GET /api/v1/catalog}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteProductDto(
        Long id,
        String name,
        String slug,
        String shortDescription,
        Double price,
        String currency,
        Long regionId,
        Long categoryId,
        List<RemoteProductImageDto> images) {
}
