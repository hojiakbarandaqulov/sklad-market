package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.order.OrderCostumerDebitResponse;
import org.example.enums.AppLanguage;
import org.example.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/orders")
public class OrderCostumerDebitController {

    private final OrderService orderService;

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("customer-debts")
    public ApiResponse<OrderCostumerDebitResponse> orderCustomerDebit(@RequestParam Long companyId,
                                                                      @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language){
        return ApiResponse.successResponse(orderService.orderCustomerDebit(companyId,language));
    }
}
