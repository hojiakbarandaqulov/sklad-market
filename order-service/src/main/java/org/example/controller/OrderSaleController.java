package org.example.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.order.*;
import org.example.enums.AppLanguage;
import org.example.service.impl.OrderSaleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderSaleController {
    private final OrderSaleService service;

    @GetMapping("/{id}/checkout")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<OrderSaleService.Checkout> checkout(@PathVariable Long id, @RequestParam(required = false) Long customerId,
                                                           @RequestHeader("Authorization") String auth,
                                                           @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.checkout(id, customerId, auth, language));
    }

    @PostMapping("/{id}/complete-sale")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<InvoiceResponse> complete(@PathVariable Long id, @Valid @RequestBody SaleRequest request,
                                                 @RequestHeader("Authorization") String auth,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.complete(id, request, auth, language));
    }

    @GetMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable Long id,
                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.invoice(id, language));
    }
}
