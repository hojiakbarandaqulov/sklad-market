package org.example.service;

import org.example.dto.kafka.UserRoleUpdateEvent;
import org.example.dto.kafka.UserUpdateRole;
import org.example.dto.kafka.UserUpdateStatus;

public interface KafkaProducerService {
    void sendUserRoleUpdate(UserUpdateRole event);

    void sendUserStatusUpdate(UserUpdateStatus event);
}
