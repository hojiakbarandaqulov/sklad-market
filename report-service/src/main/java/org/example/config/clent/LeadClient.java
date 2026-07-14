package org.example.config.clent;

import org.example.dto.dashboard.SellerStatsFilterRequest;
import org.example.dto.dashboard.internal.LeadSellerStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "lead-service")
public interface LeadClient {

    @PostMapping("/internal/leads/stats/seller/overview")
    LeadSellerStatsResponse getSellerOverview(@RequestBody SellerStatsFilterRequest request);
}
