package com.example.config.clent;

import com.example.dto.vacancy.CompanySummaryDTO;
import feign.Request;
import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.TimeUnit;

/** AI-only optional public enrichment; never modifies the ordinary Jobs Feign client defaults. */
@FeignClient(name = "company-service", contextId = "aiJobsCompanySummary", configuration = AiCompanySummaryClient.Timeouts.class)
public interface AiCompanySummaryClient {
    @GetMapping("/internal/companies/{companyId}/summary")
    CompanySummaryDTO getSummary(@PathVariable("companyId") Long companyId);

    // Deliberately NOT @Configuration: only the named Feign child context loads these beans.
    class Timeouts {
        @Bean public Request.Options requestOptions() { return new Request.Options(250, TimeUnit.MILLISECONDS, 300, TimeUnit.MILLISECONDS, true); }
        @Bean public Retryer retryer() { return Retryer.NEVER_RETRY; }
    }
}
