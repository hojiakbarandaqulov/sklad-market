package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.client.SellerWorkflowClient;
import org.example.dto.ApiResponse;
import org.example.dto.order.*;
import org.example.exp.OrderException;
import org.example.service.impl.OrderServiceImpl;
import org.example.utils.SpringSecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.Hidden
@RequestMapping("/internal/orders/leads")
@PreAuthorize("hasRole('SELLER')")
public class LeadOrderInternalController {
    private final SellerWorkflowClient leads;
    private final OrderServiceImpl orders;

    @PostMapping("/{id}/accept")
    public ApiResponse<OrderResponse> accept(@PathVariable Long id, @Valid @RequestBody LeadAcceptRequest request,
            @RequestHeader("Authorization") String authorization) {
        SellerWorkflowClient.LeadEnvelope response;
        try { response = leads.lead(id, authorization); }
        catch (feign.FeignException ex) { throw OrderException.unavailable("operation=lead.details upstream_status=" + ex.status()); }
        if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null
                || !id.equals(response.data().id()) || response.data().buyerId() == null
                || response.data().companyId() == null) throw OrderException.unavailable("operation=lead.details failure=invalid_response");
        if (!SpringSecurityUtil.getProfileId().equals(response.data().sellerId())) throw new OrderException(403,"order.access.denied");
        return ApiResponse.successResponse(orders.acceptLead(id, request, response.data()));
    }
}
