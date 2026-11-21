package org.example.service.impl;

import io.jsonwebtoken.JwtException;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.RefreshTokenDTO;
import org.example.dto.RegistrationDTO;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.*;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
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
        String keycloakId = "";
        Users users;
        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isPresent()) {
            users = optional.get();
            if (!users.getStatus().equals(GeneralStatus.IN_REGISTRATION)) {
                throw new AppBadException(messageService.getMessage("email.phone.exists", language));
            }
            users.setFirstName(dto.getFirstName());
            users.setLastName(dto.getLastName());
            users.setUsername(dto.getUsername());
            users.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
            users.setRole(mapToRole(roles));
            users.setStatus(GeneralStatus.IN_REGISTRATION);

            if (users.getKeycloakId() == null || users.getKeycloakId().isBlank()) {
                keycloakId = keycloakService.createUser(
                        dto.getFirstName(), dto.getLastName(), dto.getUsername(), dto.getPassword(), mapToRole(roles)
                );
                users.setKeycloakId(keycloakId);
            } else {
                keycloakService.updatePassword(users.getKeycloakId(), dto.getPassword());
            }
        } else {
            keycloakId = keycloakService.createUser(
                    dto.getFirstName(), dto.getLastName(), dto.getUsername(), dto.getPassword(), mapToRole(roles)
            );
            users = new Users();
            users.setFirstName(dto.getFirstName());
            users.setLastName(dto.getLastName());
            users.setUsername(dto.getUsername());
            users.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
            users.setRole(mapToRole(roles));
            users.setStatus(GeneralStatus.IN_REGISTRATION);
            users.setKeycloakId(keycloakId);
        }
        Users user = userRepository.save(users);

        keycloakService.addProfileIdAttribute(user.getKeycloakId(), user.getId(), dto.getFirstName(), dto.getLastName(), dto.getUsername(), dto.getPassword());
        kafkaProducerService.sendUserRegistered(UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .password(user.getPassword())
                .roles(user.getRole())
                .status(user.getStatus())
                .keycloakId(user.getKeycloakId())
                .build());

        if (EmailUtil.isEmail(dto.getUsername())) {
            emailSendingService.sendRegistrationEmail(dto.getUsername(), language);
        } else if (PhoneUtil.isPhone(dto.getUsername())) {
            // phone sending sms
        }
        return new ApiResponse<>(messageService.getMessage("registration.successful", language));
    }

    @Override
    public ApiResponse<String> regVerification(String token, AppLanguage lang) {
        try {
            Long profileId = JwtUtil.decodeVerRegToken(token);
            Users profile = userRepository.findById(profileId)
                    .orElseThrow(() -> new AppBadException(messageService.getMessage("verification.wrong", lang)));
            if (profile.getStatus().equals(GeneralStatus.IN_REGISTRATION)) {
                userRepository.changeStatus(profileId, GeneralStatus.ACTIVE);
                kafkaProducerService.sendUserVerified(profileId);
//                keycloakService.verifyUserEmail(profile.getUsername());
                return new ApiResponse<>(messageService.getMessage("verification.successful", lang));
            }
        } catch (JwtException e) {
            throw new AppBadException(messageService.getMessage("verification.wrong", lang));
        }
        throw new AppBadException(messageService.getMessage("verification.wrong", lang));
    }

    @Override
    public ApiResponse<String> registrationVerification(RegistrationVerificationDTO dto, AppLanguage lang) {
        try {
            Users profile = userRepository.findByUsernameAndDeletedFalse(dto.getUsername())
                    .orElseThrow(() -> new AppBadException(messageService.getMessage("verification.wrong", lang)));
            if (profile.getStatus().equals(GeneralStatus.IN_REGISTRATION)) {
                userRepository.changeStatus(profile.getId(), GeneralStatus.ACTIVE);
                kafkaProducerService.sendUserVerified(profile.getId());
//                keycloakService.verifyUserEmail(profile.getUsername());
                return new ApiResponse<>(messageService.getMessage("verification.successful", lang));
            }
        } catch (JwtException e) {
            throw new AppBadException(messageService.getMessage("verification.wrong", lang));
        }
        throw new AppBadException(messageService.getMessage("verification.wrong", lang));
    }

    @Override
    public GoogleLoginResponse complete(Jwt jwt, RegistrationSelectRoles roles, AppLanguage language) {

        validateGoogleToken(jwt, language);

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        Users users =  userRepository.findByKeycloakId(jwt.getSubject())
                .orElse(null);


        if (users != null) {
            if (Boolean.TRUE.equals(users.getDeleted())
                    || users.getStatus() != GeneralStatus.ACTIVE) {
                throw new AppBadException(
                        messageService.getMessage("wrong.status", language));
            }
            synchronizeKeycloakProfile(users);
            return new GoogleLoginResponse(users.getId(), users.getUsername(), users.getRole(), false, false);
        }


        if (roles == null) {
            return new GoogleLoginResponse(null, email, null, false, true);
        }
        Users newUser = createGoogleUser(jwt, mapToRole(roles), language);
        synchronizeKeycloakProfile(newUser);


        return new GoogleLoginResponse(newUser.getId(), newUser.getUsername(), newUser.getRole(), true, false);

    }

    private Users createGoogleUser(Jwt jwt, Roles role, AppLanguage language) {
        // complete() bu metoddan oldin JWT'dagi sub, email, email_verified
        // va Google provayderi claimini tekshirgan bo‘lishi kerak.
        String email = jwt.getClaimAsString("email").strip().toLowerCase(Locale.ROOT);

        // Shu email boshqa profilga biriktirilgan bo‘lsa,
        // foydalanuvchi Google akkauntini Keycloak orqali bog‘lashi kerak.
        if (userRepository.existsByUsernameIgnoreCase(email)) {
            throw new AppBadException(
                    messageService.getMessage("email.phone.exists", language));
        }

        Users user = new Users();
        user.setKeycloakId(jwt.getSubject());
        user.setUsername(email);
        user.setFirstName(jwt.getClaimAsString("given_name"));
        user.setLastName(jwt.getClaimAsString("family_name"));
        user.setRole(role); // complete()dagi mapToRole() BUYER yoki SELLER beradi.
        user.setStatus(GeneralStatus.ACTIVE);

        // Google orqali kiradigan profil uchun password null qoladi.
        Users saved = userRepository.saveAndFlush(user);

        // user-service shu event orqali o‘z bazasida profil yaratadi.
        kafkaProducerService.sendUserRegistered(UserRegisteredEvent.builder()
                .userId(saved.getId())
                .keycloakId(saved.getKeycloakId())
                .username(saved.getUsername())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .roles(saved.getRole())
                .status(saved.getStatus())
                .build());

        return saved;
    }


    @Override
    public ApiResponse<ProfileDTO> login(LoginDTO dto, AppLanguage language) {
        Optional<Users> optional = userRepository.findByUsernameAndDeletedFalse(dto.getUsername());
        if (optional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("username.not.found", language));
        }
        Users profile = optional.get();
        if (profile.getPassword() == null) {
            throw new AppBadException(messageService.getMessage("wrong.password", language));
        }
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

    private void synchronizeKeycloakProfile(Users user) {
        // Sklad Market bazasidagi ID'ni Keycloak user atributiga yozamiz.
        keycloakService.addProfileIdAttribute(
                user.getKeycloakId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                null
        );

        // Rol requestdan emas, bazada saqlangan profildan olinadi.
        // Google onboarding orqali faqat BUYER va SELLER rollarini biriktiramiz.
        if (user.getRole() == Roles.BUYER || user.getRole() == Roles.SELLER) {
            keycloakService.assignRoleToUser(user.getKeycloakId(), user.getRole());
        }
    }

    private void validateGoogleToken(Jwt jwt, AppLanguage language) {
        String email = jwt == null ? null : jwt.getClaimAsString("email");

        if (jwt == null
                || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || email == null || email.isBlank()
                || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                || !"google-login".equals(jwt.getClaimAsString("identity_provider"))
                || !"market-web".equals(jwt.getClaimAsString("azp"))
                || jwt.getAudience() == null
                || !jwt.getAudience().contains("auth-service")) {
            throw new AppBadException(
                    messageService.getMessage("google.login.invalid", language));
        }
    }

    private Roles mapToRole(RegistrationSelectRoles roles) {
        return switch (roles) {
            case BUYER -> Roles.BUYER;
            case SELLER -> Roles.SELLER;
        };
    }

}

