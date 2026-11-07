package org.example.service.job;

import lombok.RequiredArgsConstructor;
import org.example.service.impl.OrderSaleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="order.recovery.enabled",havingValue="true",matchIfMissing=true)
public class SaleRecoveryJob {
    private final OrderSaleService service;
    @Scheduled(fixedDelayString="${order.recovery.delay-ms:30000}")
    public void recover() { service.recover(); }
}
