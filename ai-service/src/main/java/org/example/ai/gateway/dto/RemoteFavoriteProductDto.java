package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors product-service's {@code ProductResponse} (plain camelCase, verified in source) as returned inside {@code GET /api/v1/product-favorites}'s page content. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteFavoriteProductDto(String name, String slug, Double price, String currency, String shortDescription) {
}
