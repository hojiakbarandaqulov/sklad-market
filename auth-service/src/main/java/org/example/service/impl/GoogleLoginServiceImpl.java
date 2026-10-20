package org.example.service.impl;

import org.example.dto.auth.GoogleLoginRequest;
import org.example.dto.auth.GoogleLoginResponse;
import org.example.service.GoogleIdentityService;
import org.example.service.GoogleLoginService;
import org.example.service.GoogleProfileService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import uz.sklad.security.GoogleIdentity;
import java.time.Duration;
import java.time.Instant;

@Service
public class GoogleLoginServiceImpl implements GoogleLoginService {
    private final JwtDecoder googleDecoder;
    private final GoogleIdentityService identities;
    private final GoogleProfileService profiles;

    public GoogleLoginServiceImpl(@Qualifier("googleIdTokenDecoder") JwtDecoder googleDecoder,
                                  GoogleIdentityService identities, GoogleProfileService profiles) {
        this.googleDecoder = googleDecoder;
        this.identities = identities;
        this.profiles = profiles;
    }

    @Override
    public GoogleLoginResponse login(GoogleLoginRequest request) {
        Jwt jwt;
        try {
            jwt = googleDecoder.decode(request.idToken());
        } catch (JwtException ex) {
            throw new BadCredentialsException("Google tokeni yaroqsiz yoki muddati tugagan");
        }
        GoogleIdentity identity = identities.resolve(jwt);
        profiles.recordLogin(identity.profileId());
        // Token o'zgartirilmaydi. profileId va rol server bazasidan olinadi.
        long expiresIn = Math.max(0, Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds());
        return new GoogleLoginResponse(jwt.getTokenValue(), expiresIn, "Bearer", "GOOGLE", identity);
    }
}
