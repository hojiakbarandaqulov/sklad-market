package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

@Configuration
public class GoogleLoginTokenConfig {

    @Bean("googleIdTokenDecoder")
    public JwtDecoder googleIdTokenDecoder(
            @Value("${google.client-id}") String googleClientId) {

        // Google'ning ochiq kalitlari orqali token imzosi tekshiriladi.
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(
                        "https://www.googleapis.com/oauth2/v3/certs")
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                new JwtClaimValidator<String>("iss", issuer ->
                        "https://accounts.google.com".equals(issuer)
                                || "accounts.google.com".equals(issuer));

        // Token aynan bizning Google ilovamiz uchun berilgan bo'lishi kerak.
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>("aud", audience ->
                        audience != null
                                && audience.contains(googleClientId));

        // azp kelsa, u ham bizning client ID bilan mos bo'lishi kerak.
        OAuth2TokenValidator<Jwt> clientValidator = jwt -> {
            String azp = jwt.getClaimAsString("azp");

            boolean valid = azp == null
                    ? jwt.getAudience() != null
                    && jwt.getAudience().size() == 1
                    : googleClientId.equals(azp);

            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Google client mos emas",
                            null));
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        issuerValidator,
                        audienceValidator,
                        clientValidator));

        return decoder;
    }

    @Bean("keycloakLoginTokenDecoder")
    public JwtDecoder keycloakLoginTokenDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer) {

        // Keycloak qaytargan tokenni ham imzosi va issuer'i bilan tekshiramiz.
        return JwtDecoders.fromIssuerLocation(issuer);
    }
}
