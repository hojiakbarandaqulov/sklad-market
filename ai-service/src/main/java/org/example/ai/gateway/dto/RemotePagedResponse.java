package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Mirrors product-service's {@code PagedResponse<T>} ({@code items}/{@code meta}), e.g. {@code GET /api/v1/catalog}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemotePagedResponse<T>(List<T> items, RemotePageMeta meta) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemotePageMeta(long total, int page, int perPage, int totalPages) {
    }
}
