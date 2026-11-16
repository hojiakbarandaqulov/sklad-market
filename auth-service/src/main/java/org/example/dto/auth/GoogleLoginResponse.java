package org.example.dto.auth;

import org.example.enums.Roles;

public record GoogleLoginResponse(
        Long profileId,
        String email,
        Roles role,
        boolean created,
        boolean roleSelectionRequired
) {
}
