package uz.sklad.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;

/** The signed JWT stays unchanged; platform ID and authorities belong to the Authentication. */
public final class GoogleAuthenticationToken extends JwtAuthenticationToken {
    private static final Set<String> ROLES = Set.of("BUYER", "SELLER", "ADMIN", "SUPER_ADMIN");
    private final GoogleIdentity identity;

    public GoogleAuthenticationToken(Jwt jwt, GoogleIdentity identity) {
        super(jwt, authorities(identity), identity.username());
        this.identity = identity;
    }

    private static List<SimpleGrantedAuthority> authorities(GoogleIdentity identity) {
        if (identity == null || identity.profileId() == null || identity.profileId() <= 0
                || identity.username() == null || identity.username().isBlank()
                || identity.keycloakId() == null || identity.keycloakId().isBlank()
                || !ROLES.contains(identity.role() == null ? "" : identity.role())) {
            throw new BadCredentialsException("Platform identity is invalid");
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + identity.role()));
    }

    public GoogleIdentity getIdentity() {
        return identity;
    }
}
