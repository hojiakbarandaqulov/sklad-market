package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ApiResponse;
import org.example.dto.order.*;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class OrderController {
    private final OrderService service;

    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.successResponse(service.create(request));
    }

    @GetMapping
    public ApiResponse<Page<OrderResponse>> buyer(@Valid @ModelAttribute OrderFilter filter) {
        return ApiResponse.successResponse(service.list(filter, false));
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Page<OrderResponse>> seller(@Valid @ModelAttribute OrderFilter filter) {
        return ApiResponse.successResponse(service.list(filter, true));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable @Positive Long id) {
        return ApiResponse.successResponse(service.get(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<OrderResponse> ship(@PathVariable @Positive Long id) {
        return ApiResponse.successResponse(service.ship(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable @Positive Long id) {
        return ApiResponse.successResponse(service.cancel(id));
    }
}
