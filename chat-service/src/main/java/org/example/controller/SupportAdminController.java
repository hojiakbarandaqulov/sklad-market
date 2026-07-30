package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PagedResponse;
import org.example.dto.support.SupportThreadResponse;
import org.example.enums.SupportThreadStatus;
import org.example.service.SupportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/support/chats")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class SupportAdminController {
    private final SupportService supportService;

    @Operation(summary = "Support chatlari navbatini ko‘rish")
    @GetMapping
    public ApiResponse<PagedResponse<SupportThreadResponse>> getQueue(
            @RequestParam(required = false) SupportThreadStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "30") int perPage) {
        return ApiResponse.successResponse(supportService.getAdminQueue(status, page, perPage));
    }

    @Operation(summary = "Support chatini joriy adminga biriktirish")
    @PostMapping("/{threadId}/assign")
    public ApiResponse<SupportThreadResponse> assign(@PathVariable Long threadId) {
        return ApiResponse.successResponse(supportService.assignToCurrentAdmin(threadId));
    }

    @Operation(summary = "Support chatini yopish")
    @PutMapping("/{threadId}/close")
    public ApiResponse<SupportThreadResponse> close(@PathVariable Long threadId) {
        return ApiResponse.successResponse(supportService.close(threadId));
    }
}
