package com.example.service;

import com.example.enums.ReservationStatus;
import com.example.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "control.reservations.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJob {
    private final StockReservationRepository repository;
    private final StockReservationService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${control.reservations.expiry-delay-ms:60000}",
               initialDelayString = "${control.reservations.expiry-delay-ms:60000}")
    public void releaseExpired() {
        var ids = repository.expiredIds(ReservationStatus.ACTIVE, clock.instant(), PageRequest.of(0, 100));
        for (Long id : ids) {
            try {
                // Har bir rezerv alohida tranzaksiyada: bittasining xatosi boshqalarini to'xtatmaydi.
                service.expire(id);
            } catch (RuntimeException exception) {
                log.error("Rezerv muddatini yakunlashda xato, reservationId={}", id, exception);
            }
        }
    }
}
