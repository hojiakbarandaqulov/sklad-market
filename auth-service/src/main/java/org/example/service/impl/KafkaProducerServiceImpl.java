package org.example.service.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.kafka.SendCompanyNameEvent;
import org.example.dto.kafka.SuperAdminSendKeycloakId;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.dto.kafka.UserVerifiedEvent;
import org.example.service.KafkaProducerService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@AllArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_VERIFIED = "user.verified";
    public static final String SUPER_ADMIN_KEYCLOAK_ID = "super.admin.keycloak.id";
    public static final String SEND_COMPANY_NAME = "send.company.name";

    @Override
    public void sendUserRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(USER_REGISTERED, event.getUserId().toString(), event);
        log.info("Kafka -> User registered yuborildi: {}", event.getUserId());
    }

    @Override
    public void sendUserVerified(Long userId) {
        kafkaTemplate.send(USER_VERIFIED, userId.toString(), new UserVerifiedEvent(userId));
        log.info("Kafka -> User verified yuborildi: {}", userId);
    }

    @Override
    public void sendKeycloakId(SuperAdminSendKeycloakId event) {
        kafkaTemplate.send(SUPER_ADMIN_KEYCLOAK_ID, event);
        log.info("Keycloak ID bodybuilder: {}",event.getUserId());
    }

    @Override
    public void sendCompanyName(SendCompanyNameEvent event) {
        kafkaTemplate.send(SEND_COMPANY_NAME, event.getSellerId().toString(), event);
        log.info("Company name bodybuilder: {}",event);
    }
}
