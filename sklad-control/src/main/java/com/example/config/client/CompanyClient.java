package com.example.config.client;

import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.dto.internal.CompanyBranchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "company-service", configuration = CompanyFeignConfig.class)
public interface CompanyClient {
    @GetMapping("/internal/companies/{companyId}/ownership-check")
    CompanyOwnershipResponse ownershipCheck(@PathVariable("companyId") Long companyId,
                                            @RequestParam("sellerId") Long sellerId);

    @GetMapping("/internal/companies/{companyId}/branches/{branchId}")
    CompanyBranchResponse getBranch(@PathVariable("companyId") Long companyId,
                                   @PathVariable("branchId") Long branchId);
}
