package com.example.service.impl;

import com.example.dto.vacancy.VacancyCreatedEvent;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class VacancyNotificationCommitTest {
    KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
    KafkaProducerServiceImpl producer = new KafkaProducerServiceImpl(kafka);
    VacancyCreatedEvent event = VacancyCreatedEvent.builder().vacancyId(77L).ownerId(901L).build();
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void updateNotificationIsPublishedOnlyAfterCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        producer.sendVacancyCreated(event);
        verifyNoInteractions(kafka);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafka).send("vacancy.created", "77", event);
        verifyNoMoreInteractions(kafka);
    }
    @Test void rollbackDoesNotPublishNotification() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        producer.sendVacancyCreated(event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(kafka);
    }
    @Test void nonTransactionalSavesUseExistingTopicAndVacancyKey() {
        producer.sendVacancyCreated(event);
        verify(kafka).send("vacancy.created", "77", event);
        verifyNoMoreInteractions(kafka);
    }
}