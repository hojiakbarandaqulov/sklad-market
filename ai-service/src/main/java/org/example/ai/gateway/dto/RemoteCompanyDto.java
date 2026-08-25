package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/** Mirrors company-service's {@code CompanySlugMapResponse}, as returned by {@code GET /api/v1/companies/{slug}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCompanyDto(
        Long id,
        String name,
        String slug,
        String status,
        Long regionId,
        Long districtId,
        String address,
        String phonePrimary,
        String phoneSecondary,
        String website,
        LocalDate companyCreatedDate,
        String logoUrl) {

    /** Compatibility constructor for the historical public company payload. */
    public RemoteCompanyDto(
            Long id,
            String name,
            String slug,
            String status,
            Long regionId,
            Long districtId,
            String address,
            String phonePrimary,
            String phoneSecondary,
            String website,
            LocalDate companyCreatedDate) {
        this(id, name, slug, status, regionId, districtId, address, phonePrimary, phoneSecondary,
                website, companyCreatedDate, null);
    }
}
