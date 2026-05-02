package org.example.service;

import org.example.dto.kafka.SendCompanyNameEvent;

public interface KafkaConsumerService {
    void sendCompanyName(SendCompanyNameEvent event);
}
