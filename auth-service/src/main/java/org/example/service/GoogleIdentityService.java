package org.example.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.sklad.security.GoogleIdentity;
import uz.sklad.security.GoogleIdentityResolver;

public interface GoogleIdentityService extends GoogleIdentityResolver {
    GoogleIdentity link(Authentication authentication, Jwt verifiedGoogleToken);
}
