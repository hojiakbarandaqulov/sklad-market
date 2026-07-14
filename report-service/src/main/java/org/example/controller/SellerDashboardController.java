package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.dashboard.SellerDashboardResponse;
import org.example.service.SellerDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seller/dashboard")
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerDashboardResponse> getDashboard(
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "6") Integer months
    ) {
        return ApiResponse.successResponse(sellerDashboardService.getDashboard(companyId, months));
    }
}
