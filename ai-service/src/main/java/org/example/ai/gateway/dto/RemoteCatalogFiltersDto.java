package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Mirrors product-service's {@code CatalogFilterResponse}, as returned by {@code GET /api/v1/catalog/filters}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCatalogFiltersDto(
        Double minPrice,
        Double maxPrice,
        Map<String, List<String>> attributes) {
}
