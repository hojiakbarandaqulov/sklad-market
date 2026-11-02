package org.example.utils;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.example.exp.OrderException;

public final class SpringSecurityUtil {
    private SpringSecurityUtil() { }
    public static Long getProfileId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && auth.isAuthenticated()) {
            Object value = jwt.getToken().getClaim("profileId");
            try { long id = Long.parseLong(String.valueOf(value)); if (id > 0) return id; }
            catch (NumberFormatException ignored) { }
        }
        throw new OrderException(403, "Token ichida profileId yo'q");
    }
    public static boolean isSeller() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));
    }
}
