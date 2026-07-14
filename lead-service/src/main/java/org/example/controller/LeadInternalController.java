package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.dashboard.SellerLeadStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;
import org.example.service.InternalLeadStatsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/leads")
public class LeadInternalController {

    private final InternalLeadStatsService internalLeadStatsService;

    @PostMapping("/stats/seller/overview")
    public SellerLeadStatsResponse sellerOverview(@RequestBody SellerStatsFilterRequest request) {
        // Dashboard uchun lead statistikani shu endpoint bitta joydan beradi.
        return internalLeadStatsService.getSellerOverview(request);
    }
}
