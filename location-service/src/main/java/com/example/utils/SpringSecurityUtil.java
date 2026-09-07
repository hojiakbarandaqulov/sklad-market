package com.example.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Slf4j
public class SpringSecurityUtil {

    public static Long getProfileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            log.info("Token claims: {}", jwt.getClaims());
            Object claim = jwt.getClaim("profileId");
            if (claim instanceof Long value) {
                return value;
            }
            if (claim instanceof Integer value) {
                return value.longValue();
            }
            if (claim instanceof String value && !value.isBlank()) {
                return Long.parseLong(value);
            }
        }
        return null;
    }
}
