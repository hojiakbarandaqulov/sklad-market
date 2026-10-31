package org.example.config.clent;

import org.springframework.cloud.openfeign.FeignClient;
import org.example.config.SkladControlFeignConfig;
import org.example.dto.ApiResponse;
import org.example.dto.dashboard.internal.ControlStatisticsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "sklad-control", configuration = SkladControlFeignConfig.class)
public interface SkladControl {
    @GetMapping("/internal/control/statistics")
    ApiResponse<ControlStatisticsResponse> getStatistics(@RequestParam("companyId") Long companyId);
}
