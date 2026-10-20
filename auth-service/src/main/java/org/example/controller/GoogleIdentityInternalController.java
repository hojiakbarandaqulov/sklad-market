package org.example.controller;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sklad.security.GoogleAuthenticationToken;
import uz.sklad.security.GoogleIdentity;

@RestController
@RequestMapping("/internal/auth/google")
public class GoogleIdentityInternalController {
    @PostMapping("/resolve")
    @PreAuthorize("isAuthenticated()")
    public GoogleIdentity resolve(Authentication authentication) {
        if (!(authentication instanceof GoogleAuthenticationToken google)) {
            throw new BadCredentialsException("Google bearer token required");
        }
        return google.getIdentity();
    }
}
