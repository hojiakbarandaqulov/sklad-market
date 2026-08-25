package org.example.controller;

import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.dto.AiUserRateLimitDto;
import org.example.dto.ApiResponse;
import org.example.dto.UpdateAiUserRateLimitRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/admin/rate-limits")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AiRateLimitAdminController {

    private final AiChatRateLimitService rateLimitService;

    public AiRateLimitAdminController(AiChatRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<List<AiUserRateLimitDto>> list() {
        return ApiResponse.successResponse(rateLimitService.list());
    }

    @PutMapping("/{userSub}")
    public ApiResponse<AiUserRateLimitDto> update(
            @PathVariable String userSub,
            @RequestBody UpdateAiUserRateLimitRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        return ApiResponse.successResponse(rateLimitService.update(
                userSub, request.requestsPerMinute(), request.dailyTokenBudget()));
    }

    @DeleteMapping("/{userSub}")
    public ApiResponse<AiUserRateLimitDto> reset(@PathVariable String userSub) {
        return ApiResponse.successResponse(rateLimitService.reset(userSub));
    }
}
