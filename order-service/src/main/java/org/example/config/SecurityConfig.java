package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.time.Clock;
import java.util.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${server.domain}") String domain) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", domain));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*")); cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", cors);
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> access = jwt.getClaimAsMap("realm_access");
            if (access == null || !(access.get("roles") instanceof List<?> roles)) return List.of();
            return roles.stream().filter(String.class::isInstance).map(String.class::cast)
                    .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        });
        return http.csrf(AbstractHttpConfigurer::disable).cors(c -> c.configurationSource(source))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
}
