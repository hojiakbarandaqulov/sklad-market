package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.enums.ProductModerationStatus;
import org.example.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@RestController @RequiredArgsConstructor
@RequestMapping("/internal/products")
@PreAuthorize("hasRole('CONTROL_ORDER_WRITE')")
public class OrderProductInternalController {
    private final ProductService products;
    @GetMapping("/{id}/order-summary")
    public ResponseEntity<OrderProductSummary> summary(@PathVariable Long id) {
        var found = products.findByIdAndDeletedAtIsNull(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        var p = found.get();
        boolean orderable = Boolean.TRUE.equals(p.getIsActive())
                && p.getModerationStatus() == ProductModerationStatus.APPROVED
                && p.getPrice() != null && p.getPrice().signum() > 0
                && p.getPriceType() != null && p.getPriceType().name().equals("FIXED");
        return ResponseEntity.ok(new OrderProductSummary(p.getId(), p.getCompanyId(), p.getSellerId(), p.getName(), p.getPrice(),
                p.getCurrency() == null ? null : p.getCurrency().name(), p.getMinProduct(), orderable));
    }
    public record OrderProductSummary(Long id, Long companyId, Long sellerId, String name, BigDecimal price,
                                      String currency, Long minimumQuantity, boolean orderable) { }
}
