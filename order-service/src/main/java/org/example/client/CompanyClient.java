package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "company-service", url = "${COMPANY_SERVICE_URL:}")
public interface CompanyClient {
    @GetMapping("/internal/companies/{id}/ownership-check")
    Ownership ownership(@PathVariable Long id, @RequestParam Long sellerId);
    record Ownership(Long companyId, boolean exists, boolean owner, boolean active) { }
}
