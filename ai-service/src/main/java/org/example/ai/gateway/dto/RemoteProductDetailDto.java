package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Compact view of product-service's {@code ProductDetailResponse}, as returned by
 * {@code GET /api/v1/products/slug/{slug}}. Several source fields are snake_case on the wire
 * ({@code @JsonProperty} on the source DTO) — mirrored exactly here. {@code id} is the internal
 * numeric product id (verified present on {@code ProductDetailResponse}) — needed by
 * {@code draft_lead} (Phase 4) to build a real {@code LeadCreateRequest.productId}, since the
 * model only ever deals in slugs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteProductDetailDto(
        Long id,
        String name,
        String slug,
        String description,
        @JsonProperty("short_description") String shortDescription,
        Double price,
        String currency,
        String status,
        @JsonProperty("region_id") Long regionId,
        @JsonProperty("district_id") Long districtId,
        List<RemoteProductImageDto> images,
        RemoteCompanySummary company,
        RemoteCategorySummary category) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCompanySummary(Long id, String name, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteCategorySummary(Long id, String name, String slug) {
    }
}
