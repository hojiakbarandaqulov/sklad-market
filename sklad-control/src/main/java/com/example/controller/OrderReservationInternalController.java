package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.reservation.*;
import com.example.service.StockReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/internal/control/orders/{orderId}/reservation")
@PreAuthorize("hasRole('CONTROL_ORDER_WRITE')")
public class OrderReservationInternalController {
    private final StockReservationService service;
    @PostMapping public ApiResponse<ReservationDTO> reserve(@PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderReservationRequest request) {
        return ApiResponse.successResponse(service.reserveOrder(orderId, request));
    }
    @GetMapping public ApiResponse<ReservationDTO> get(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "GET"));
    }
    @PostMapping("/commit") public ApiResponse<ReservationDTO> commit(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "COMMIT"));
    }
    @PostMapping("/release") public ApiResponse<ReservationDTO> release(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "RELEASE"));
    }
}
