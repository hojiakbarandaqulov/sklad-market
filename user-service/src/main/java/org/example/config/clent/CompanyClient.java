package org.example.config.clent;

import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/internal/companies/owned?sellerId={sellerId}")
    List<Long> ownedCompany(@PathVariable("sellerId") Long sellerId);


    @GetMapping("/internal/companies/{companyId}/summary")
    CompanyInternalSummaryResponse summary(@PathVariable("companyId") Long companyId);
}
