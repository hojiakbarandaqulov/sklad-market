package com.example.config.clent;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/internal/companies/by-region/ids")
    List<Long> getCompanyIdsByRegion(@RequestParam("regionId") Long regionId);

    @GetMapping("/internal/companies/owned")
    List<Long> getOwnedCompanyIds(@RequestParam Long sellerId);

}
