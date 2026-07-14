package org.example.config.clent;

import org.example.dto.internal.CompanySummaryResponse;
import org.example.dto.report.ReportBlockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "company-service")
public interface CompanyClient {
    @GetMapping("/internal/companies/owned")
    List<Long> getOwnedCompanyIds(@RequestParam Long sellerId);

    @GetMapping("/internal/companies/stats/pending-count")
    Map<String, Long> getPendingCount();

    @PutMapping("/internal/companies/{id}/block")
    void block(@PathVariable Long id, @RequestBody ReportBlockRequest request);

    @GetMapping("/internal/companies/{id}/summary")
    CompanySummaryResponse getSummary(@PathVariable Long id);
}
