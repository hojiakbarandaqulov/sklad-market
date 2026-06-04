package org.example.service;

import org.example.dto.kafka.CompanyCreateEvent;
import org.example.dto.kafka.UserRoleUpdateEvent;
import org.example.enums.Roles;

public interface KafkaProducerService {

    void onCompanyCreated(CompanyCreateEvent event);
}
