package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors category-service's new {@code CategoryAttributeResponse} (PLAN.md Phase 6 additive
 * endpoint {@code GET /api/v1/categories/{slug}/attributes}, plain camelCase, verified in source).
 * {@code dataType} is the real {@code DataType} enum wire value: {@code TEXT|NUMBER|BOOLEAN|SELECT}
 * — no {@code STRING}/{@code DATE} exist on this platform. {@code optionsJson} has NO enforced
 * shape anywhere in category-service (raw opaque {@code TEXT} column, zero parsing logic in the
 * codebase) — {@code org.example.ai.seller.CategoryAttributeSchema} best-effort-parses it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCategoryAttributeDto(
        Long id,
        String code,
        String label,
        String dataType,
        Boolean isRequired,
        Boolean isFilterable,
        String optionsJson,
        Integer sortOrder) {
}
