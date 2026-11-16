package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.RefreshTokenDTO;
import org.example.dto.RegistrationDTO;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.*;
import org.example.enums.AppLanguage;
import org.example.enums.RegistrationSelectRoles;
import org.example.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;


    @PostMapping("/registration")
    public ApiResponse<String> registration(@Valid @RequestBody RegistrationDTO dto,
                                            @RequestParam(value = "roles", required = false) RegistrationSelectRoles roles,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return authService.registration(dto, roles, language);
    }

    @PostMapping("/google/complete")
    public ApiResponse<GoogleLoginResponse> completeGoogleLogin(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "roles", required = false)
            RegistrationSelectRoles roles,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ")
            AppLanguage language) {

        GoogleLoginResponse complete = authService.complete(jwt, roles, language);
        return ApiResponse.successResponse(complete);
    }
   /* @GetMapping("/verification/{token}")
    public ApiResponse<String> registrationVerification(@PathVariable("token") String token,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage lang) {
        return authService.regVerification(token, lang);
    }*/

    @PutMapping("verification")
    public ApiResponse<String> verification(@Valid @RequestBody RegistrationVerificationDTO dto,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage lang){
        return authService.registrationVerification(dto,lang);
    }


    @PostMapping("/registration/login")
    public ApiResponse<ProfileDTO> login(@Valid @RequestBody LoginDTO dto,
                                         @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return authService.login(dto, language);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponseDTO> refresh(@RequestBody @Valid RefreshTokenDTO dto,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return authService.refresh(dto, language);
    }

    @PostMapping("/registration/reset")
    public ApiResponse<String> resent(@Valid @RequestBody ResetPasswordDTO dto,
                                      @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return authService.resetPassword(dto, language);
    }

    @PostMapping("/registration/reset-password/confirm")
    public ApiResponse<String> resentPassword(@Valid @RequestBody UpdatePasswordDTO dto,
                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return authService.resetPasswordConfirm(dto, language);
    }

}






