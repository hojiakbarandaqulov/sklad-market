package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.availability.StockAvailabilityRequest;
import com.example.dto.availability.StockAvailabilityResponse;
import com.example.service.ControlQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Market serveri uchun. Faqat mavjud miqdor beriladi; kompaniyaning xususiy ombor jurnali emas. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/control/availability")
@PreAuthorize("hasAnyRole('SELLER', 'CONTROL_AVAILABILITY_READ')")
public class StockAvailabilityInternalController {
    private final ControlQueryService queryService;

    @PostMapping
    public ApiResponse<List<StockAvailabilityResponse>> getAvailability(
            @Valid @RequestBody StockAvailabilityRequest request) {
        return ApiResponse.successResponse(queryService.availability(request));
    }
}
