package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.context.annotation.Import(uz.sklad.security.MarketJwtConfiguration.class)
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private uz.sklad.security.DirectGoogleAuthenticationManagerResolver directGoogleResolver;

    @Value("${server.domain}")
    private String domain;

    /**
     * Comma-separated CORS allowed-origin patterns. The profile default (empty) keeps only the
     * service's own {@code server.domain} in production; {@code application-local.yml}/dev overrides
     * add {@code http://localhost:*} so the Vite dev server can call through. Prod-hardening,
     * PLAN.md §4.2: don't allow localhost credentialed origins on the public deployment.
     */
    @Value("${ai.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginsProperty;

    public static final String[] AUTH_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_WHITELIST).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/ai/guest/status").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/ai/guest/session", "/api/v1/ai/guest/messages").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(auth2 -> {
                    if (directGoogleResolver != null) {
                        auth2.authenticationManagerResolver(directGoogleResolver);
                    } else {
                        auth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()));
                    }
                });

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>();
        for (String origin : allowedOriginsProperty.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty() && !allowedOrigins.contains(trimmed)) {
                allowedOrigins.add(trimmed);
            }
        }
        // The service's own domain is always permitted (in prod this is the only entry).
        if (domain != null && !domain.isBlank() && !allowedOrigins.contains(domain)) {
            allowedOrigins.add(domain);
        }
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Language", "X-AI-Error-Code"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) {
                return List.of();
            }

            Object rolesObject = realmAccess.get("roles");
            if (!(rolesObject instanceof List<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}
