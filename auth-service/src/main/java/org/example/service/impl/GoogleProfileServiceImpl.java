package org.example.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.GoogleLoginRequest;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.entity.Users;
import org.example.enums.GeneralStatus;
import org.example.exp.AppBadException;
import org.example.repository.UserRepository;
import org.example.service.GoogleLoginService;
import org.example.service.GoogleProfileService;
import org.example.service.KafkaProducerService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleProfileServiceImpl implements GoogleProfileService {

    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;


    @Transactional
    @Override
    public Users prepare(Jwt googleToken, String keycloakId) {
        Users user = userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    String email = googleToken.getClaimAsString("email")
                            .trim()
                            .toLowerCase(Locale.ROOT);

                    // Birinchi kirishda lokal user yaratiladi.
                    // Yangi foydalanuvchiga faqat BUYER beriladi.
                    userRepository.insertGoogleUser(
                            keycloakId,
                            email,
                            googleToken.getClaimAsString("given_name"),
                            googleToken.getClaimAsString("family_name"));

                    return userRepository.findByKeycloakId(keycloakId)
                            .orElseThrow(() -> new AppBadException(
                                    "Bu email boshqa akkauntga bog‘langan. "
                                            + "Avval mavjud akkauntga kiring"));
                });

        // O'chirilgan yoki bloklangan user qayta faollashtirilmaydi.
        if (Boolean.TRUE.equals(user.getDeleted())
                || user.getStatus() != GeneralStatus.ACTIVE) {
            throw new AppBadException("Akkaunt faol emas");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .password(user.getPassword())
                .roles(user.getRole())
                .status(user.getStatus())
                .keycloakId(user.getKeycloakId())
                .build();

        // Baza commit bo'lgach profil yaratish eventi yuboriladi.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaProducerService.sendUserRegistered(event);
                    }
                });

        return user;
    }
}
