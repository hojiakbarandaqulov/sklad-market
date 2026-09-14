package org.example.dto;

import org.example.enums.CompanyType;

/** The pair (type, id) identifies a company or branch location. */
public record CompanyLocationResponse(
        Long id,
        CompanyType type,
        Long companyId,
        String name,
        String address,
        String lat,
        String lng
) {
}
