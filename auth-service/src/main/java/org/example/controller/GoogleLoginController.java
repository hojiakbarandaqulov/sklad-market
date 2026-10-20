package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.GoogleLoginRequest;
import org.example.service.GoogleLoginService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/google")
public class GoogleLoginController {

    private final GoogleLoginService googleLoginService;


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDTO>> login(
            @Valid @RequestBody GoogleLoginRequest request) {

        TokenResponseDTO tokens =
                googleLoginService.login(request);

        // Login tokenlari brauzer/proxy keshida saqlanmasin.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(tokens));
    }
}
