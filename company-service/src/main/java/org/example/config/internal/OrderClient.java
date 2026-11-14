package org.example.config.internal;

import org.example.dto.ApiResponse;
import org.example.dto.orderInternal.OrderCostumerDebitResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/internal/orders/customer-debts")
    ApiResponse<OrderCostumerDebitResponse> orderDebit(
            @RequestParam("companyId") Long companyId,
            @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE) String language,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
