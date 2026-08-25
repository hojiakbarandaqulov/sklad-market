package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Mirrors Jackson's default {@code Page}/{@code PageImpl} serialization, e.g. {@code GET /api/v1/categories}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteSpringPage<T>(List<T> content) {
}
