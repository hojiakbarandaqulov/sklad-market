package com.example.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SpringSecurityUtil {
    private SpringSecurityUtil() {
    }

    public static Long getProfileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) return null;
        Object claim = jwt.getToken().getClaim("profileId");
        if (claim instanceof Long value) return value > 0 ? value : null;
        if (claim instanceof Integer value) return value > 0 ? value.longValue() : null;
        if (claim instanceof String value) {
            try {
                long id = Long.parseLong(value);
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + role)::equals);
    }
}
