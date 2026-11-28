package org.example.dto.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompanyMemberCreateRequest(@NotNull @Positive Long profileId) {
}
