package org.example.client;

import org.example.client.dto.CompanyOwnershipResponse;
import org.example.client.dto.CompanySummaryResponse;
import org.example.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "company-service",
        url = "${services.company.url}",
        configuration = FeignClientConfig.class
)
public interface CompanyClient {

    @GetMapping("/internal/companies/{companyId}/ownership-check")
    CompanyOwnershipResponse checkOwnership(@PathVariable Long companyId, @RequestParam Long buyerId);

    @GetMapping("/internal/companies/owned")
    List<Long> getOwnedCompanyIds(@RequestParam Long sellerId);

    @GetMapping("/internal/companies/{companyId}/summary")
    CompanySummaryResponse getSummary(@PathVariable Long companyId);
}
