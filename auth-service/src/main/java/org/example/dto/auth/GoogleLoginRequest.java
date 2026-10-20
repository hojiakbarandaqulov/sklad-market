package org.example.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID tokeni majburiy")
        String idToken
) {
}
