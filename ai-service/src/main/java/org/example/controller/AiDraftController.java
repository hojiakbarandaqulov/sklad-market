package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.DraftActionResponse;
import org.example.dto.DraftConfirmRequest;
import org.example.dto.DraftDetailsResponse;
import org.example.security.AiSecurityUtil;
import org.example.service.ActionDraftConfirmService;
import org.example.service.ActionDraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The only entry point that can turn a draft into a real platform write (PLAN.md Phase 4): called
 * directly by the UI's Confirm/Cancel buttons, outside the model's tool-calling loop.
 */
@RestController
@RequestMapping("/api/v1/ai/drafts")
public class AiDraftController {

    private final ActionDraftConfirmService confirmService;
    private final ActionDraftService draftService;

    public AiDraftController(ActionDraftConfirmService confirmService, ActionDraftService draftService) {
        this.confirmService = confirmService;
        this.draftService = draftService;
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftDetailsResponse> get(@PathVariable UUID id) {
        String userSub = AiSecurityUtil.requireSub();
        return ApiResponse.successResponse(draftService.getDetails(userSub, id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<DraftActionResponse> confirm(
            @PathVariable UUID id,
            @RequestBody(required = false) DraftConfirmRequest overrides,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String userSub = AiSecurityUtil.requireSub();
        String bearerToken = AiSecurityUtil.requireBearerToken();
        return ApiResponse.successResponse(confirmService.confirm(userSub, id, overrides, bearerToken, acceptLanguage));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DraftActionResponse> cancel(@PathVariable UUID id) {
        String userSub = AiSecurityUtil.requireSub();
        return ApiResponse.successResponse(confirmService.cancel(userSub, id));
    }
}
