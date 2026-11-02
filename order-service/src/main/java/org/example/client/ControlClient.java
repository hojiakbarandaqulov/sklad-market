package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@FeignClient(name = "sklad-control", url = "${CONTROL_SERVICE_URL:}")
public interface ControlClient {
    @PostMapping("/internal/control/orders/{orderId}/reservation")
    Envelope reserve(@PathVariable Long orderId, @RequestBody ReserveRequest request);
    @GetMapping("/internal/control/orders/{orderId}/reservation")
    Envelope get(@PathVariable Long orderId, @RequestParam Long companyId);
    @PostMapping("/internal/control/orders/{orderId}/reservation/commit")
    Envelope commit(@PathVariable Long orderId, @RequestParam Long companyId);
    @PostMapping("/internal/control/orders/{orderId}/reservation/release")
    Envelope release(@PathVariable Long orderId, @RequestParam Long companyId);
    record ReserveRequest(Long companyId, Long buyerId, Instant expiresAt, List<Item> items) { }
    record Item(Long productId, BigDecimal quantity) { }
    record Envelope(boolean success, Reservation data) { }
    record Reservation(Long id, Long companyId, Long orderId, String status) { }
}
