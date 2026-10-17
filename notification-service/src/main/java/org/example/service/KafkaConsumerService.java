package org.example.service;

import org.example.dto.event.CompanyCreateEvent;
import org.example.dto.event.ProductCreatedEvent;
import org.example.dto.event.VacancyCreatedEvent;

public interface KafkaConsumerService {
    void onProductCreated(ProductCreatedEvent event);
    void onCompanyCreated(CompanyCreateEvent event);
    void onVacancyCreated(VacancyCreatedEvent event);
}
