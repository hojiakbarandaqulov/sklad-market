package org.example.service.job;

import lombok.RequiredArgsConstructor;
import org.example.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class OrderRecoveryJob {

    private final OrderService orderService;

    // Aloqa uzilishi yoki servis qayta ishga tushishidan keyin tugamagan buyurtmalar davom ettiriladi.
    @Scheduled(
            fixedDelayString = "${order.recovery.delay-ms:60000}",
            initialDelayString = "${order.recovery.delay-ms:60000}"
    )
    public void synchronize() {
        orderService.recover(org.example.enums.AppLanguage.UZ);
    }
}
