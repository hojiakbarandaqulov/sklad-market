package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Mirrors product-service's admin {@code ProductResponse} (plain camelCase, verified in source:
 * {@code GET /api/v1/admin/products/moderation-queue}, {@code GET /api/v1/admin/products/{id}} —
 * both {@code hasAnyRole('ADMIN','SUPER_ADMIN')}). {@code updatedAt} is deliberately omitted here:
 * the source DTO has the field but the service never populates it (commented out server-side).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteAdminProductDto(
        Long id,
        Long companyId,
        Long sellerId,
        Long categoryId,
        String name,
        String slug,
        String shortDescription,
        String description,
        Double price,
        String currency,
        Map<String, Object> attributes,
        String status,
        Boolean isActive,
        String rejectReason,
        String createdAt) {
}
