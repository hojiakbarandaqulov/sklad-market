package org.example.service;

import org.example.dto.kafka.SendCompanyNameEvent;
import org.example.dto.kafka.SuperAdminSendKeycloakId;
import org.example.dto.kafka.UserRegisteredEvent;
import org.example.dto.kafka.UserVerifiedEvent;

public interface KafkaProducerService {
    void sendUserRegistered(UserRegisteredEvent event);
    void sendUserVerified(Long userId);
    void sendKeycloakId(SuperAdminSendKeycloakId event);

    void sendCompanyName(SendCompanyNameEvent sendCompanyNameEvent);
}
