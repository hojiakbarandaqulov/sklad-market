package org.example.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.enums.AppLanguage;
import org.example.enums.OrderStatus;
import org.example.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/orders/lead-statuses")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class LeadOrderStatusInternalController {
    private final OrderService orders;

    @PostMapping
    public ApiResponse<Map<Long, OrderStatus>> statuses(
            @RequestBody @Size(min = 1, max = 100) List<@NotNull @Positive Long> orderIds,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(orders.getLeadOrderStatuses(orderIds, language));
    }
}
