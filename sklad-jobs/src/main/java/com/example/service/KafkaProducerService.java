package com.example.service;

import com.example.dto.vacancy.VacancyCreatedEvent;

public interface KafkaProducerService {

    void sendVacancyCreated(VacancyCreatedEvent vacancyCreatedEvent);

}
