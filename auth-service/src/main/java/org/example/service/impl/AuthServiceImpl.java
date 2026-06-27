package org.example.service.impl;

import io.jsonwebtoken.JwtException;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.RefreshTokenDTO;
import org.example.dto.RegistrationDTO;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.LoginDTO;
import org.example.dto.auth.ProfileDTO;
import org.example.dto.auth.ResetPasswordDTO;
import org.example.dto.auth.UpdatePasswordDTO;
import org.example.dto.kafka.SendCompanyNameEvent;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.entity.Users;
import org.example.enums.AppLanguage;
import org.example.enums.GeneralStatus;
import org.example.enums.RegistrationSelectRoles;
import org.example.enums.Roles;
import org.example.exp.AppBadException;
import org.example.repository.UserRepository;
import org.example.service.*;
import org.example.utils.EmailUtil;
import org.example.utils.JwtUtil;
import org.example.utils.PhoneUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final ResourceBundleService messageService;
    private final KafkaProducerService kafkaProducerService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final EmailSendingService emailSendingService;
    private final LoginAttemptService loginAttemptService;
    private final KeycloakService keycloakService;
    private final EmailHistoryService emailHistoryService;


    @Override
    public ApiResponse<String> registration(RegistrationDTO dto, RegistrationSelectRoles roles, AppLanguage language) {

        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isPresent()) {
            Users users = optional.get();
            if (users.getStatus().equals(GeneralStatus.IN_REGISTRATION)) {
                userRepository.delete(users);
            } else {
                throw new AppBadException(messageService.getMessage("email.phone.exists", language));
            }
        }

        String keycloakId = keycloakService.createUser(
                dto.getFirstName(), dto.getLastName(), dto.getUsername(), dto.getPassword(), mapToRole(roles)
        );

        Users entity = new Users();
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setUsername(dto.getUsername());
        entity.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
        entity.setStatus(GeneralStatus.IN_REGISTRATION);
        entity.setRole(mapToRole(roles));
        entity.setKeycloakId(keycloakId);
        Users save = userRepository.save(entity);

        keycloakService.addProfileIdAttribute(keycloakId, save.getId(), dto.getFirstName(), dto.getLastName(), dto.getUsername(), dto.getPassword());
        kafkaProducerService.sendUserRegistered(UserRegisteredEvent.builder()
                .userId(entity.getId())
                .username(entity.getUsername())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .password(entity.getPassword())
                .roles(entity.getRole())
                .status(entity.getStatus())
                .keycloakId(keycloakId)
                .build());

        if (EmailUtil.isEmail(dto.getUsername())) {
            emailSendingService.sendRegistrationEmail(dto.getUsername(), entity.getId());
        } else if (PhoneUtil.isPhone(dto.getUsername())) {
            // phone sending sms
        }
        return new ApiResponse<>(messageService.getMessage("registration.successful", language));
    }

    @Override
    public ApiResponse<String> regVerification(String token, AppLanguage lang) {
        try {
            Long profileId = JwtUtil.decodeVerRegToken(token);
            Users profile = userRepository.getReferenceById(profileId);
            if (profile.getStatus().equals(GeneralStatus.IN_REGISTRATION)) {
                userRepository.changeStatus(profileId, GeneralStatus.ACTIVE);
                kafkaProducerService.sendUserVerified(profileId);
//                keycloakService.verifyUserEmail(profile.getUsername());
                return new ApiResponse<>(messageService.getMessage("verification.successful", lang));
            }
        } catch (JwtException e) {
            throw new RuntimeException(e);
        }
        throw new AppBadException(messageService.getMessage("verification.wrong", lang));
    }

    @Override
    public ApiResponse<ProfileDTO> login(LoginDTO dto, AppLanguage language) {
        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("username.not.found", language));
        }
        Users profile = optional.get();
        LocalDateTime timestamp = LocalDateTime.now();
        if (profile.getLockedUntil() != null && profile.getLockedUntil().isAfter(timestamp)) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), profile.getLockedUntil()) + 1;
            throw new AppBadException(
                    messageService.getMessage("account.locked", language) + " " + minutesLeft + " " + messageService.getMessage("minute", language)
            );
        }

        if (!bCryptPasswordEncoder.matches(dto.getPassword(), profile.getPassword())) {
            loginAttemptService.handleFailedAttempt(profile);
            throw new AppBadException(messageService.getMessage("wrong.password", language));
        }
        if (!profile.getStatus().equals(GeneralStatus.ACTIVE)) {
            throw new AppBadException(messageService.getMessage("wrong.status", language));
        }

        TokenResponseDTO token = keycloakService.getToken(dto.getUsername(), dto.getPassword());
        profile.setFailedLoginCount(0);
        profile.setLockedUntil(null);
        profile.setLastLoginAt(timestamp);
//        userRepository.save(profile);

        ProfileDTO response = new ProfileDTO();
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setUsername(profile.getUsername());
        response.setRole(profile.getRole());
        response.setAccessToken(token.getAccessToken());
        response.setRefreshToken(token.getRefreshToken());
        response.setExpiresIn(token.getExpiresIn());
        profile.setLastLoginAt(timestamp);
        userRepository.save(profile);
        return new ApiResponse<>(response);
    }

    @Override
    public ApiResponse<String> resetPassword(ResetPasswordDTO dto, AppLanguage language) {
        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("username.password.wrong", language));
        }
        Users profile = optional.get();
        if (!profile.getStatus().equals(GeneralStatus.ACTIVE)) {
            throw new AppBadException(messageService.getMessage("wrong.status", language));
        }
        emailSendingService.sentResetPasswordEmail(dto.getUsername(), language);
        return new ApiResponse<>(messageService.getMessage("reset.password.response", language));
    }

    @Override
    public ApiResponse<String> resetPasswordConfirm(UpdatePasswordDTO dto, AppLanguage language) {
        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("verification.wrong", language));
        }
        Users profile = optional.get();
        if (!profile.getStatus().equals(GeneralStatus.ACTIVE)) {
            throw new AppBadException(messageService.getMessage("wrong.status", language));
        }

        if (profile.getKeycloakId() == null || profile.getKeycloakId().isBlank()) {
            throw new AppBadException(messageService.getMessage("keycloak.id.null", language));
        }
        emailHistoryService.check(dto.getUsername(), dto.getConfirmCode());
        keycloakService.updatePassword(profile.getKeycloakId(), dto.getNewPassword());
        userRepository.updatePassword(profile.getId(), bCryptPasswordEncoder.encode(dto.getNewPassword()));
        return new ApiResponse<>(messageService.getMessage("reset.password.success", language));
    }

    @Override
    public ApiResponse<TokenResponseDTO> refresh(RefreshTokenDTO dto, AppLanguage language) {
        try {
            TokenResponseDTO token = keycloakService.refreshToken(dto.getRefreshToken());
            return new ApiResponse<>(token);
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("refresh.token.invalid.expired", language));
        }
    }

    private Roles mapToRole(RegistrationSelectRoles roles) {
        return switch (roles) {
            case BUYER -> Roles.BUYER;
            case SELLER -> Roles.SELLER;
        };
    }

}

