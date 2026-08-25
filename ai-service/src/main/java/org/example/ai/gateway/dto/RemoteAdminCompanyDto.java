package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors company-service's admin {@code CompanyResponseDTO} (plain camelCase, verified in source:
 * {@code GET /api/v1/admin/companies/moderation-queue}, {@code hasAnyRole('ADMIN','SUPER_ADMIN')}).
 * Known platform gap (documented, not worked around): company-service exposes NO per-id admin
 * detail endpoint and the DTO carries no {@code rejectReason} field even though the entity has one
 * — {@code Company.rejectReason} is set on reject/block but never copied into this response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteAdminCompanyDto(
        Long id,
        String name,
        String slug,
        String shortDescription,
        String description,
        Long regionId,
        Long districtId,
        String address,
        String verificationStatus,
        String createdAt) {
}
