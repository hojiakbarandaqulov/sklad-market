package org.example.config.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.kafka.SuperAdminSendKeycloakId;
import org.example.entity.Users;
import org.example.repository.UserRepository;
import org.example.service.KafkaProducerService;
import org.example.service.KeycloakService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminInitializer {

    private final UserRepository profileRepository;
    private final KeycloakService keycloakService;
    private final KafkaProducerService kafkaProducerService;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        Optional<Users> superAdmin = profileRepository.findByUsernameAndDeletedFalse(
                "xojiakbarandaqulov@gmail.com"
        );
        if (superAdmin.isEmpty()) {
            log.info("Super admin does not exist");
            return;
        }

        Users users = superAdmin.get();

        if (users.getKeycloakId() != null) {
            log.info("Super admin allaqachon Keycloak da bor");
            return;
        }

        try {
            String keycloakId = keycloakService.createUser(
                    users.getFirstName(),
                    users.getLastName(),
                    users.getUsername(),
                    "super-admin",
                    users.getRole()
            );

            users.setKeycloakId(keycloakId);
            users.setFailedLoginCount(0);
            profileRepository.save(users);
            SuperAdminSendKeycloakId adminKeycloakId = new SuperAdminSendKeycloakId();
            adminKeycloakId.setKeycloakId(keycloakId);
            adminKeycloakId.setUserId(users.getId());
            kafkaProducerService.sendKeycloakId(adminKeycloakId);

            keycloakService.addProfileIdAttribute(keycloakId, users.getId());

            log.info("Super admin Keycloak ga muvaffaqiyatli qo'shildi");
        } catch (Exception e) {
            log.error("Super admin Keycloak ga qo'shishda xato", e);
        }
    }
}
