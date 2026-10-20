package org.example.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import uz.sklad.security.GoogleIdentity;

public record GoogleLoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType,
        String provider,
        GoogleIdentity user
) {
}
