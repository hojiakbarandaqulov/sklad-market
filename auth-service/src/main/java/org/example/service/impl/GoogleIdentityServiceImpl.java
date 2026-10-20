package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.entity.Users;
import org.example.enums.GeneralStatus;
import org.example.repository.UserRepository;
import org.example.service.GoogleIdentityService;
import org.example.service.GoogleProfileService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.sklad.security.GoogleAuthenticationToken;
import uz.sklad.security.GoogleIdentity;
import uz.sklad.security.GoogleJwtDecoders;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GoogleIdentityServiceImpl implements GoogleIdentityService {
    private final GoogleProfileService profiles;
    private final UserRepository users;

    @Override
    public GoogleIdentity resolve(Jwt verifiedGoogleToken) {
        requireGoogleClaims(verifiedGoogleToken);
        Users user = profiles.findOrCreate(verifiedGoogleToken);
        user = profiles.provision(user.getId());
        return identity(user);
    }

    @Override
    @Transactional
    public GoogleIdentity link(Authentication authentication, Jwt verifiedGoogleToken) {
        requireGoogleClaims(verifiedGoogleToken);
        if (!(authentication instanceof JwtAuthenticationToken keycloak)
                || authentication instanceof GoogleAuthenticationToken) {
            throw new BadCredentialsException("Avval mavjud akkauntga Keycloak tokeni bilan kiring");
        }
        Users authenticated = users.findByKeycloakId(keycloak.getToken().getSubject())
                .orElseThrow(() -> new BadCredentialsException("Akkaunt topilmadi"));
        Users user = users.findForGoogleProvisioning(authenticated.getId())
                .orElseThrow(() -> new BadCredentialsException("Akkaunt topilmadi"));
        requireActive(user);
        String subject = verifiedGoogleToken.getSubject();
        Users linked = users.findByGoogleSubject(subject).orElse(null);
        if ((linked != null && !linked.getId().equals(user.getId()))
                || (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(subject))) {
            throw new BadCredentialsException("Google akkaunti allaqachon bog'langan");
        }
        user.setGoogleSubject(subject);
        return identity(users.saveAndFlush(user));
    }

    private void requireGoogleClaims(Jwt jwt) {
        String issuer = jwt.getClaimAsString("iss");
        String email = jwt.getClaimAsString("email");
        if (issuer == null || !GoogleJwtDecoders.ISSUERS.contains(issuer)
                || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || email == null || email.isBlank()
                || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new BadCredentialsException("Google identity yaroqsiz");
        }
    }

    static void requireActive(Users user) {
        if (Boolean.TRUE.equals(user.getDeleted()) || user.getStatus() != GeneralStatus.ACTIVE
                || user.getRole() == null || (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new BadCredentialsException("Akkaunt faol emas");
        }
    }

    private GoogleIdentity identity(Users user) {
        requireActive(user);
        return new GoogleIdentity(user.getId(), user.getUsername(), user.getRole().name(), user.getKeycloakId());
    }
}
