package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors report-service's {@code ReportResponse} (plain camelCase, verified in source:
 * {@code GET /api/v1/admin/reports}, {@code hasAnyRole('SUPER_ADMIN','ADMIN')}). {@code reasonCode}
 * is the platform's REAL reason enum (verified: {@code SAME|FAKE|OFFENSIVE|DUPLICATE|SCAM}) — it
 * lives here, on user-submitted complaints against an existing product/company/chat, NOT on
 * product-service/company-service's own new-submission moderation (which uses a free-text reason
 * string instead; PLAN.md assumed otherwise, corrected after reading the real source).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteReportDto(
        Long id,
        String status,
        String targetType,
        Long targetId,
        String reasonCode,
        String createdDate) {
}
