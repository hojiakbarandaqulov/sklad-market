package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.order.*;
import org.example.enums.AppLanguage;
import org.example.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.Hidden
@RequestMapping("/internal/orders/leads")
@PreAuthorize("hasRole('SELLER')")
public class LeadOrderInternalController {
    private final OrderService orders;

    @PostMapping("/{id}/accept")
    public ApiResponse<OrderResponse> accept(@PathVariable Long id, @Valid @RequestBody LeadAcceptRequest request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(orders.acceptLead(id, request, authorization, language));
    }
}
