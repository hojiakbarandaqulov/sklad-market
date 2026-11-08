package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RequestHeader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@FeignClient(name = "sklad-control")
public interface ControlClient {
    @GetMapping("/internal/control/orders/{orderId}/reservation/availability")
    List<Availability> availability(@PathVariable("orderId") Long orderId, @RequestParam("companyId") Long companyId, @RequestHeader("Accept-Language") String language);
    @PostMapping("/internal/control/orders/{orderId}/reservation/complete")
    Envelope complete(@PathVariable("orderId") Long orderId, @RequestBody SaleReservationRequest request, @RequestHeader("Accept-Language") String language);
    record Availability(Long productId, BigDecimal reservedQuantity, BigDecimal availableQuantity, BigDecimal maxSaleQuantity) {}
    record SaleReservationRequest(Long companyId, List<Item> items) {}

    @PostMapping("/internal/control/orders/{orderId}/reservation")
    Envelope reserve(@PathVariable Long orderId, @RequestBody ReserveRequest request, @RequestHeader("Accept-Language") String language);

    @GetMapping("/internal/control/orders/{orderId}/reservation")
    Envelope get(@PathVariable Long orderId, @RequestParam Long companyId, @RequestHeader("Accept-Language") String language);

    @PostMapping("/internal/control/orders/{orderId}/reservation/commit")
    Envelope commit(@PathVariable Long orderId, @RequestParam Long companyId, @RequestHeader("Accept-Language") String language);

    @PostMapping("/internal/control/orders/{orderId}/reservation/release")
    Envelope release(@PathVariable Long orderId, @RequestParam Long companyId, @RequestHeader("Accept-Language") String language);

    // Control omborni o'zi tanlaydi. Order faqat Market product ID'si va miqdorni yuboradi.
    record ReserveRequest(
            Long companyId,
            Long buyerId,
            Instant expiresAt,
            List<Item> items
    ) {
    }

    record Item(
            Long productId,
            BigDecimal quantity
    ) {
    }

    record Envelope(
            boolean success,
            Reservation data
    ) {
    }

    record Reservation(
            Long id,
            Long companyId,
            Long orderId,
            String status
    ) {
    }
}
