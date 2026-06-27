package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.kafka.SuperAdminSendKeycloakId;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.dto.kafka.UserRoleUpdateEvent;
import org.example.dto.kafka.UserVerifiedEvent;
import org.example.entity.UserProfile;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;
import org.example.repository.UserProfileRepository;
import org.example.service.KafkaConsumerService;
import org.example.service.KeycloakService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerServiceImpl implements KafkaConsumerService {

    private final UserProfileRepository profileRepository;
    private final KeycloakService keycloakService;

    @KafkaListener(
            topics = "user.registered",
            groupId = "user-service-group",
            properties = {"spring.json.value.default.type=org.example.dto.kafka.UserRegisteredEvent"}
    )
    @Override
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Kafka ← user.registered keldi, userId={}",
                event.getUserId());

        if (profileRepository.existsByUserId(event.getUserId())) {
            log.warn("Profil allaqachon bor: {}", event.getUserId());
            return;
        }
        Optional<UserProfile> profile = profileRepository.findByUsernameAndDeletedFalse(event.getUsername());
        profile.ifPresent(profileRepository::delete);

        UserProfile profileMap = UserProfile.builder()
                .userId(event.getUserId())
                .username(event.getUsername())
                .firstName(event.getFirstName())
                .lastName(event.getLastName())
                .password(event.getPassword())
                .roles(event.getRoles())
                .status(event.getStatus())
                .keycloakId(event.getKeycloakId())
                .build();

        profileRepository.save(profileMap);
        log.info("✓ Profil yaratildi, userId={}", event.getUserId());
    }

    @KafkaListener(
            topics = "user.verified",
            groupId = "user-service-group",
            properties = {"spring.json.value.default.type=org.example.dto.kafka.UserVerifiedEvent"}
    )
    public void onUserVerified(UserVerifiedEvent event) {
        log.info("Kafka ← user.verified keldi, userId={}",
                event.getUserId());
        Optional<UserProfile> profile = profileRepository.findByUserId(event.getUserId());
        if (profile.isPresent()) {
            UserProfile verifiedProfile = profile.get();
            verifiedProfile.setStatus(GeneralStatus.ACTIVE);
            profileRepository.save(verifiedProfile);
        }
    }

    @KafkaListener(
            topics = "user.role.update",
            groupId = "user-service-group",
            properties = {"spring.json.value.default.type=org.example.dto.kafka.UserRoleUpdateEvent"}
    )
    @Override
    public void onUserRoleUpdate(UserRoleUpdateEvent event) {
        log.info("Kafka ← user.role.update keldi, userId={}", event.getUserId());

        Optional<UserProfile> profile = profileRepository.findByUserId(event.getUserId());
        if (profile.isPresent()) {
            UserProfile verifiedProfile = profile.get();

            String keycloakId = verifiedProfile.getKeycloakId();

            if (keycloakId == null) {
                log.warn("Profile ning keycloakId si yo'q: userId={}", event.getUserId());
                return;
            }

            keycloakService.removeRole(keycloakId, verifiedProfile.getRoles());

            keycloakService.assignRoleToUser(keycloakId, Roles.SELLER);
            log.info("Keycloak da role SELLER ga yangilandi: keycloakId={}", keycloakId);

            verifiedProfile.setRoles(Roles.SELLER);
            profileRepository.save(verifiedProfile);
        }
    }

    @KafkaListener(
            topics = "super.admin.keycloak.id",
            groupId = "user-service-group",
            properties = {"spring.json.value.default.type=org.example.dto.kafka.SuperAdminSendKeycloakId"}
    )
    @Override
    public void onKeycloakId(SuperAdminSendKeycloakId event) {
        UserProfile profile = profileRepository.findByUserIdAndDeletedFalse(event.getUserId());
        if (profile == null) {
            log.warn("Profile topilmadi: userId={}", event.getUserId());
            return;
        }
        profile.setKeycloakId(event.getKeycloakId());
        profileRepository.save(profile);
        log.info("Profile keycloakId yangilandi: userId={}", event.getUserId());
    }
}
