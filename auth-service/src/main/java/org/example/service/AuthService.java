package org.example.service;

import io.jsonwebtoken.JwtException;
import org.example.dto.ApiResponse;
import org.example.dto.RefreshTokenDTO;
import org.example.dto.RegistrationDTO;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.*;
import org.example.entity.Users;
import org.example.enums.AppLanguage;
import org.example.enums.RegistrationSelectRoles;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
public interface AuthService {
     ApiResponse<String> registration(RegistrationDTO dto, RegistrationSelectRoles roles, AppLanguage language);

     ApiResponse<String> regVerification(String token, AppLanguage lang);

     ApiResponse<ProfileDTO> login(LoginDTO dto, AppLanguage language);

     ApiResponse<String> resetPassword(ResetPasswordDTO dto, AppLanguage language);

     ApiResponse<String> resetPasswordConfirm(UpdatePasswordDTO dto, AppLanguage language);

     ApiResponse<TokenResponseDTO> refresh(RefreshTokenDTO dto,AppLanguage language);

    ApiResponse<String> registrationVerification(RegistrationVerificationDTO dto, AppLanguage lang);
}
