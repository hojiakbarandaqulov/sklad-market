package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ApiResponse;
import org.example.dto.auth.GoogleLoginRequest;
import org.example.dto.auth.GoogleLoginResponse;
import org.example.service.GoogleIdentityService;
import org.example.service.GoogleLoginService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import uz.sklad.security.GoogleIdentity;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleLoginController {
    private final GoogleLoginService googleLoginService;
    private final GoogleIdentityService identities;
    private final JwtDecoder googleDecoder;

    public GoogleLoginController(GoogleLoginService googleLoginService, GoogleIdentityService identities,
                                 @Qualifier("googleIdTokenDecoder") JwtDecoder googleDecoder) {
        this.googleLoginService = googleLoginService;
        this.identities = identities;
        this.googleDecoder = googleDecoder;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> login(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.successResponse(googleLoginService.login(request)));
    }

    @PostMapping("/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GoogleIdentity>> link(Authentication authentication,
            @Valid @RequestBody GoogleLoginRequest request) {
        try {
            GoogleIdentity identity = identities.link(authentication, googleDecoder.decode(request.idToken()));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.successResponse(identity));
        } catch (JwtException ex) {
            throw new BadCredentialsException("Google tokeni yaroqsiz yoki muddati tugagan");
        }
    }
}
