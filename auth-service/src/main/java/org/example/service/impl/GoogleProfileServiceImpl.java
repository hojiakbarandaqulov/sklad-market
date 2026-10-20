package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.entity.Users;
import org.example.repository.UserRepository;
import org.example.service.GoogleProfileService;
import org.example.service.KafkaProducerService;
import org.example.service.KeycloakService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleProfileServiceImpl implements GoogleProfileService {
    private final UserRepository users;
    private final KeycloakService keycloak;
    private final KafkaProducerService kafka;

    @Override
    @Transactional
    public Users findOrCreate(Jwt jwt) {
        Users user = users.findByGoogleSubject(jwt.getSubject()).orElseGet(() -> {
            String email = jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT);
            users.insertDirectGoogleUser(jwt.getSubject(), email,
                    jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
            return users.findByGoogleSubject(jwt.getSubject()).orElseThrow(() ->
                    new BadCredentialsException("Bu email band. Mavjud akkauntga kirib Google akkauntini bog'lang"));
        });
        GoogleIdentityServiceImpl.requireActive(user);
        return user;
    }

    @Override
    @Transactional
    public Users provision(Long profileId) {
        Users user = locked(profileId);
        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank()) {
            // Parol yaratilmaydi. profileId va Google sub bilan qayta urinish ham xavfsiz.
            user.setKeycloakId(keycloak.createGoogleUser(user.getFirstName(), user.getLastName(),
                    user.getUsername(), user.getGoogleSubject(), user.getId(), user.getRole()));
            users.saveAndFlush(user);
            publishAfterCommit(user);
        }
        return user;
    }

    @Override
    @Transactional
    public void recordLogin(Long profileId) {
        Users user = locked(profileId);
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        // Oldingi Kafka jo'natishi uzilgan bo'lsa, user-service idempotent tarzda tiklaydi.
        publishAfterCommit(user);
    }

    private Users locked(Long profileId) {
        Users user = users.findForGoogleProvisioning(profileId)
                .orElseThrow(() -> new BadCredentialsException("Akkaunt topilmadi"));
        GoogleIdentityServiceImpl.requireActive(user);
        return user;
    }

    private void publishAfterCommit(Users user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(user.getId()).username(user.getUsername())
                .firstName(user.getFirstName()).lastName(user.getLastName())
                .roles(user.getRole()).status(user.getStatus()).keycloakId(user.getKeycloakId()).build();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafka.sendUserRegistered(event);
            }
        });
    }
}
