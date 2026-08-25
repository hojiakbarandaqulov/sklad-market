package org.example.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Conversations/drafts are owner-scoped by the Keycloak {@code sub} claim (PLAN.md §4.2 item 9) —
 * deliberately distinct from other services' custom {@code profileId} claim, which identifies the
 * platform user row rather than the OIDC subject.
 */
public final class AiSecurityUtil {

    private AiSecurityUtil() {
    }

    public static String requireSub() {
        Jwt jwt = currentJwt();
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT has no subject claim");
        }
        return sub;
    }

    /**
     * The raw bearer token, for pass-through to downstream tool calls (PLAN.md §4.2 item 1: the
     * user's own JWT is the only downstream credential). Must be captured on the request thread —
     * the chat turn later runs on {@code aiChatExecutor}, which does not propagate the
     * {@link SecurityContextHolder} context, so callers must read this before dispatching async
     * work and pass it down explicitly, exactly like {@link #requireSub()}. Never log this value.
     */
    public static String requireBearerToken() {
        return currentJwt().getTokenValue();
    }

    /** Stable human-readable label for AI-only administration; authorization still uses sub. */
    public static String currentUsername() {
        Jwt jwt = currentJwt();
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) username = jwt.getClaimAsString("email");
        return username == null || username.isBlank() ? null : username.trim();
    }

    /** Comma-joined, sorted role names (without the ROLE_ prefix), for informational storage only. */
    public static String currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "";
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        return String.join(",", roles);
    }

    /**
     * The caller's FULL live role set (without the {@code ROLE_} prefix), read fresh from this
     * request's JWT. Used for tool-registry gating (PLAN.md Phase 6) instead of
     * {@code Conversation.userRole} — that field is a single-role snapshot taken once at
     * conversation creation ({@code roles.split(",")[0]}, alphabetically first) and goes stale the
     * moment a caller holds more than one role or a role changes after the conversation exists.
     * Must be captured on the request thread, exactly like {@link #requireBearerToken()} — the
     * chat turn later runs on {@code aiChatExecutor}, which does not propagate
     * {@link SecurityContextHolder}.
     */
    public static Set<String> currentRoleSet() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            roles.add(name.startsWith("ROLE_") ? name.substring(5) : name);
        }
        return roles;
    }

    private static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("No authenticated JWT in security context");
        }
        return jwtAuth.getToken();
    }
}
