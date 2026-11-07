package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="company-service",contextId="saleCustomers")
public interface SaleCustomerClient {
    @GetMapping("/api/v1/customers/{id}")
    Envelope get(@PathVariable("id") Long id, @RequestHeader("Authorization") String auth);
    record Envelope(Boolean success, Customer data) {}
    record Customer(Long id, Long companyId, String name, String phone, String contractNumber, Integer paymentTermDays) {}
}
