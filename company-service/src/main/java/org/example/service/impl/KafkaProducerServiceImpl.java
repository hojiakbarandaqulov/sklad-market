package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.example.dto.kafka.CompanyCreateEvent;
import org.example.dto.kafka.UserRoleUpdateEvent;
import org.example.enums.Roles;
import org.example.service.KafkaProducerService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class KafkaProducerServiceImpl implements KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String COMPANY_CREATED = "company.created";

    @Override
    public void onCompanyCreated(CompanyCreateEvent event) {
        kafkaTemplate.send(COMPANY_CREATED, String.valueOf(event.getCompanyId()),event);
        log.info("Company created");
    }
}
