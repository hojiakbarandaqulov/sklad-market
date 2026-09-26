package com.example.config.clent;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.CompanySummaryDTO;
import com.example.dto.vacancy.VacancyContactsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "company-service")
public interface CompanyClient {
    @GetMapping("/internal/companies/{companyId}/summary")
    CompanySummaryDTO getSummary(@PathVariable("companyId") Long companyId);

    @GetMapping("/api/v1/companies/{slug}")
    ApiResponse<VacancyContactsDTO> getPublicContacts(
            @PathVariable("slug") String slug, @RequestHeader("Accept-Language") String language);

    @GetMapping("/internal/companies/by-region/ids")
    List<Long> getCompanyIdsByRegion(@RequestParam("regionId") Long regionId);

    @GetMapping("/internal/companies/owned")
    List<Long> getOwnedCompanyIds(@RequestParam Long sellerId);

}
