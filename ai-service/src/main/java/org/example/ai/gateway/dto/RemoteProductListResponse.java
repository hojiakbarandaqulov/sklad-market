package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors product-service's {@code ProductListResponse} envelope from {@code GET /api/v1/products/all}:
 * {@code items} plus snake_case paging fields ({@code per_page}, {@code total_elements},
 * {@code total_pages}) — the item objects themselves are camelCase (see {@link RemoteIndexProductDto}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteProductListResponse(
        List<RemoteIndexProductDto> items,
        Integer page,
        @JsonProperty("per_page") Integer perPage,
        @JsonProperty("total_elements") Long totalElements,
        @JsonProperty("total_pages") Integer totalPages) {
}
