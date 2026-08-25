package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors every backend service's {@code ApiResponse<T>} envelope ({@code success/data/message}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayEnvelope<T>(Boolean success, T data, String message) {
}
