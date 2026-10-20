package uz.sklad.security;

import org.springframework.security.oauth2.jwt.Jwt;

public interface GoogleIdentityResolver {
    GoogleIdentity resolve(Jwt verifiedGoogleToken);
}
