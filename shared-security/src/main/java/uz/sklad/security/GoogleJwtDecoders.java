package uz.sklad.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;
import java.util.Set;

public final class GoogleJwtDecoders {
    public static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private GoogleJwtDecoders() {
    }

    public static NimbusJwtDecoder create(String clientId, String jwkSetUri) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("GOOGLE_CLIENT_ID is required for direct Google authentication");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<String>("iss", value -> value != null && ISSUERS.contains(value));
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                values -> values != null && values.contains(clientId));
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            String azp = jwt.getClaimAsString("azp");
            boolean validClient = azp == null ? jwt.getAudience() != null && jwt.getAudience().size() == 1 : clientId.equals(azp);
            String email = jwt.getClaimAsString("email");
            boolean valid = validClient && jwt.getExpiresAt() != null
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && email != null && !email.isBlank()
                    && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Google identity claims are invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience, claims));
        return decoder;
    }
}
