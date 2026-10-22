package uz.sklad.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "auth.google.direct-enabled", havingValue = "true")
public class MarketJwtConfiguration {
    @Bean
    public DirectGoogleAuthenticationManagerResolver directGoogleAuthenticationManagerResolver(
            Environment environment, ObjectMapper objectMapper, ObjectProvider<GoogleIdentityResolver> localIdentities) {
        String clientId = environment.getProperty("google.client-id", environment.getProperty("GOOGLE_CLIENT_ID", ""));
        String issuer = environment.getRequiredProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwks = environment.getProperty("auth.google.jwk-set-uri", "https://www.googleapis.com/oauth2/v3/certs");
        GoogleIdentityResolver identities = localIdentities.getIfAvailable();
        if (identities == null) {
            identities = new RemoteGoogleIdentityResolver(environment.getProperty("auth.google.resolve-url",
                    "http://127.0.0.1:8081/internal/auth/google/resolve"), objectMapper);
        }
        return new DirectGoogleAuthenticationManagerResolver(issuer, GoogleJwtDecoders.create(clientId, jwks), identities);
    }
}
