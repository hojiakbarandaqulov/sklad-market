package org.example.service.job;

import org.example.service.OrderService;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "order.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class OrderRecoveryJob {
    private final OrderService service;
    @Scheduled(fixedDelayString = "${order.recovery.delay-ms:60000}", initialDelayString = "${order.recovery.delay-ms:60000}")
    public void synchronize() { service.recover(); }
}
