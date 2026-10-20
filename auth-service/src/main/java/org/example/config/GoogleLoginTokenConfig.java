package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import uz.sklad.security.GoogleJwtDecoders;

@Configuration
public class GoogleLoginTokenConfig {
    @Bean("googleIdTokenDecoder")
    public JwtDecoder googleIdTokenDecoder(@Value("${google.client-id:${GOOGLE_CLIENT_ID:}}") String clientId,
            @Value("${auth.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri) {
        return GoogleJwtDecoders.create(clientId, jwkSetUri);
    }

    @Bean("keycloakLoginTokenDecoder")
    @Lazy
    public JwtDecoder keycloakLoginTokenDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return JwtDecoders.fromIssuerLocation(issuer);
    }
}
