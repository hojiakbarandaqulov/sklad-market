package org.example.clent;

import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "company-service"
        /*url = "${services.company-service.url}",
        configuration = FeignClientConfig.class,
        dismiss404 = true*/
)
public interface CompanyClient {

    @GetMapping("/internal/companies/{companyId}/summary")
    CompanyInternalSummaryResponse getSummary(@PathVariable Long companyId);
}
