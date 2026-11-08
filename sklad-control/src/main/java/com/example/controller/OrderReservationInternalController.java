package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.enums.AppLanguage;
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
    @GetMapping("/availability")
    public java.util.List<java.util.Map<String,Object>> availability(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return service.orderAvailability(companyId, orderId, language);
    }
    @PostMapping("/complete")
    public ApiResponse<ReservationDTO> complete(@PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderSaleRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.completeOrder(orderId, request, language));
    }
    @PostMapping
    public ApiResponse<ReservationDTO> reserve(@PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderReservationRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.reserveOrder(orderId, request, language));
    }
    @GetMapping
    public ApiResponse<ReservationDTO> get(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "GET", language));
    }
    @PostMapping("/commit")
    public ApiResponse<ReservationDTO> commit(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "COMMIT", language));
    }
    @PostMapping("/release")
    public ApiResponse<ReservationDTO> release(@PathVariable @Positive Long orderId,
            @RequestParam @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.orderReservation(companyId, orderId, "RELEASE", language));
    }
}
