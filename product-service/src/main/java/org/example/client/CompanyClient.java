package org.example.client;

import org.example.client.dto.CompanyBranchSummaryResponse;
import org.example.client.dto.CompanyMapSummaryRequest;
import org.example.client.dto.CompanyMapSummaryResponse;
import org.example.client.dto.CompanyOwnershipResponse;
import org.example.client.dto.CompanySummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "company-service")
public interface CompanyClient {

    @GetMapping("/internal/companies/{companyId}/ownership-check")
    CompanyOwnershipResponse checkOwnership(@PathVariable Long companyId, @RequestParam Long buyerId);

    @GetMapping("/internal/companies/owned")
    List<Long> getOwnedCompanyIds(@RequestParam Long sellerId);

    @GetMapping("/internal/companies/{companyId}/summary")
    CompanySummaryResponse getSummary(@PathVariable Long companyId);

    @GetMapping("/internal/companies/{companyId}/branches/{branchId}")
    CompanyBranchSummaryResponse getBranch(@PathVariable Long companyId, @PathVariable Long branchId);

    @PostMapping("/internal/companies/map-summary")
    List<CompanyMapSummaryResponse> getMapSummaries(@RequestBody CompanyMapSummaryRequest request);
}
