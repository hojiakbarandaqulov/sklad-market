package com.example.service.impl;

import com.example.dto.vacancy.VacancyCreatedEvent;
import com.example.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private final KafkaTemplate<Object, Object> kafkaTemplate;


    private static final String VACANCY_CREATED = "vacancy.created";

    @Override
    public void sendVacancyCreated(VacancyCreatedEvent vacancyCreatedEvent) {
        kafkaTemplate.send(VACANCY_CREATED, String.valueOf(vacancyCreatedEvent.vacancyId()), vacancyCreatedEvent);
        log.info("vacancy created  success...");
    }
}
