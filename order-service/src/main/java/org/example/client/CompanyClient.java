package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/internal/companies/{id}/ownership-check")
    Ownership ownership(@PathVariable Long id, @RequestParam Long sellerId);

    record Ownership(
            Long companyId,
            boolean exists,
            boolean owner,
            boolean active
    ) {
    }
}
