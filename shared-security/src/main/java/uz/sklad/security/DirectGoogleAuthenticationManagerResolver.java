package uz.sklad.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class DirectGoogleAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {
    private final JwtIssuerAuthenticationManagerResolver delegate;

    public DirectGoogleAuthenticationManagerResolver(String keycloakIssuer, JwtDecoder googleDecoder,
                                                      GoogleIdentityResolver identities) {
        JwtAuthenticationProvider google = new JwtAuthenticationProvider(googleDecoder);
        google.setJwtAuthenticationConverter(jwt -> new GoogleAuthenticationToken(jwt, identities.resolve(jwt)));
        AuthenticationManager googleManager = new ProviderManager(google);

        // Only the configured Keycloak issuer is discovered. An untrusted iss never becomes a URL request.
        AtomicReference<AuthenticationManager> keycloak = new AtomicReference<>();
        AuthenticationManager keycloakManager = authentication -> {
            AuthenticationManager manager = keycloak.get();
            if (manager == null) {
                synchronized (keycloak) {
                    manager = keycloak.get();
                    if (manager == null) {
                        manager = keycloakManager(JwtDecoders.fromIssuerLocation(keycloakIssuer));
                        keycloak.set(manager);
                    }
                }
            }
            return manager.authenticate(authentication);
        };
        Map<String, AuthenticationManager> managers = Map.of(keycloakIssuer, keycloakManager,
                "https://accounts.google.com", googleManager, "accounts.google.com", googleManager);
        AuthenticationManagerResolver<String> issuers = managers::get;
        delegate = new JwtIssuerAuthenticationManagerResolver(issuers);
    }

    private AuthenticationManager keycloakManager(JwtDecoder decoder) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");
            if (!(roles instanceof List<?> values)) return List.of();
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .<org.springframework.security.core.GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
