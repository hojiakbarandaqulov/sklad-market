package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.enums.AppLanguage;
import org.example.enums.LeadSource;
import org.example.enums.LeadStatus;
import org.example.service.LeadService;
import org.example.service.impl.LeadOrderDisplayStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/leads")
public class LeadController {
    private final LeadService leadService;
    private final LeadOrderDisplayStatusService orderDisplayStatuses;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadCreateRequest request,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(leadService.create(request, language));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<PagedResponse<LeadResponse>> buyerLeads(@RequestParam(required = false) LeadStatus status,
                                                               @RequestParam(required = false) LeadSource source,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int perPage,
                                                               @RequestHeader("Authorization") String authorization,
                                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        PagedResponse<LeadResponse> leads = leadService.getBuyerLeads(status, source, page, perPage, language);
        orderDisplayStatuses.applyOrderStatuses(leads.getItems(), authorization, language);
        return ApiResponse.successResponse(leads);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<LeadResponse> getById(@PathVariable Long id,
                                             @RequestHeader("Authorization") String authorization,
                                             @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        LeadResponse lead = leadService.getById(id, language);
        orderDisplayStatuses.applyOrderStatuses(List.of(lead), authorization, language);
        return ApiResponse.successResponse(lead);
    }

    @PatchMapping("/{id}/items")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<LeadResponse> updateQuantities(@PathVariable Long id,
            @Valid @RequestBody LeadQuantityUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(leadService.updateQuantities(id, request, language));
    }

    @DeleteMapping("cancel/{id}")
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<Boolean> cancel(@PathVariable Long id,
                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(leadService.cancel(id, language));
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<PagedResponse<LeadResponse>> sellerLeads(@RequestParam(required = false) Long companyId,
                                                                @RequestParam(required = false) LeadStatus status,
                                                                @RequestParam(required = false) LeadSource source,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int perPage,
                                                                @RequestHeader("Authorization") String authorization,
                                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        PagedResponse<LeadResponse> leads = leadService.getSellerLeads(companyId, status, source, page, perPage, language);
        orderDisplayStatuses.applyOrderStatuses(leads.getItems(), authorization, language);
        return ApiResponse.successResponse(leads);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<LeadResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody LeadStatusUpdateRequest request,
                                                  @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(leadService.updateStatus(id, request, language));
    }

}
