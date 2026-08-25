package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PagedResponse;
import org.example.dto.chat.WsTokenResponse;
import org.example.dto.support.SupportCreateRequest;
import org.example.dto.support.SupportMessageResponse;
import org.example.dto.support.SupportOpenResponse;
import org.example.enums.SupportThreadType;
import org.example.service.SupportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/chats")
public class SupportController {
    private final SupportService supportService;

    @Operation(summary = "Buyer yoki seller uchun support chatini ochish")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping("/create")
    public ApiResponse<SupportOpenResponse> createThread(
            @Valid @RequestBody(required = false) SupportCreateRequest request) {
        return ApiResponse.successResponse(supportService.openThread(request));
    }

    @Operation(summary = "Seller uchun banner yaratish bo‘yicha admin chatini ochish")
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/banner/create")
    public ApiResponse<SupportOpenResponse> createBannerChat(
            @Valid @RequestBody(required = false) SupportCreateRequest request) {

        return ApiResponse.successResponse(
                supportService.createThread(
                        request,
                        SupportThreadType.BANNER_REQUEST
                )
        );
    }
    @Operation(summary = "Support chat tarixini pagination bilan olish")

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{threadId}/messages")
    public ApiResponse<PagedResponse<SupportMessageResponse>> getMessages(
            @PathVariable Long threadId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "30") int perPage,
            @RequestParam(value = "before_id", required = false) Long beforeId) {
        return ApiResponse.successResponse(supportService.getMessages(threadId, page, perPage, beforeId));
    }

    @Operation(summary = "Support WebSocket uchun qisqa muddatli token olish")
    @PreAuthorize("hasAnyRole('BUYER', 'SELLER', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/ws-token")
    public ApiResponse<WsTokenResponse> issueWsToken() {
        return ApiResponse.successResponse(supportService.issueWsToken());
    }
}
