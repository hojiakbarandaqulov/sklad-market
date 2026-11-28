package org.example.dto.member;

import org.example.enums.CompanyMemberRole;

import java.time.LocalDateTime;

public record CompanyMemberResponse(
        Long id, Long companyId, Long profileId, CompanyMemberRole role, LocalDateTime createdDate, Long createdBy
) {
}
