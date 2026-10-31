package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.statistics.ControlStatisticsDTO;
import com.example.enums.AppLanguage;
import com.example.service.ControlStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Report-service sotuvchining JWT tokenini uzatib, ombor statistikasini shu yerdan oladi. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/control/statistics")
@PreAuthorize("hasRole('SELLER')")
public class ControlStatisticsInternalController {
    private final ControlStatisticsService service;

    @GetMapping
    public ApiResponse<ControlStatisticsDTO> getStatistics(@RequestParam("companyId") Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.getStatistics(companyId, language));
    }
}
