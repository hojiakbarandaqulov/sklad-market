package org.example.utils;

import org.example.exp.OrderException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SpringSecurityUtil {

    private SpringSecurityUtil() {
    }

    public static Long getProfileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && authentication.isAuthenticated()) {
            Object profileIdClaim = jwtAuthentication.getToken().getClaim("profileId");

            try {
                long profileId = Long.parseLong(String.valueOf(profileIdClaim));

                if (profileId > 0) {
                    return profileId;
                }
            } catch (NumberFormatException ignored) {
                // Raqamga aylantirib bo'lmaydigan claim foydalanuvchi ID'si sifatida qabul qilinmaydi.
            }
        }

        throw new OrderException(403, "order.profile.required");
    }

    public static boolean isSeller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SELLER"));
    }
}
