package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@FeignClient(name="lead-service")
public interface SellerWorkflowClient {
    @GetMapping("/api/v1/leads/{id}")
    LeadEnvelope lead(@PathVariable("id") Long id, @RequestHeader("Authorization") String auth);
    record LeadEnvelope(Boolean success, Lead data) {}
    record Lead(Long id, Long buyerId, Long sellerId, Long companyId, String status,
            String contactName, String contactPhone, String deliveryAddress, String comment, List<Item> items) {}
    record Item(Long productId, BigDecimal quantity, String productNameSnapshot, BigDecimal priceSnapshot) {}
}
