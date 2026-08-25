package org.example.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.ai.guardrail.AiQuotaProtected;
import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.dto.SendMessageRequest;
import org.example.security.AiSecurityUtil;
import org.example.service.AiChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;

/**
 * The streaming turn endpoint. Ownership of the conversation is checked synchronously (a
 * not-found/not-owned conversation is a plain 404 via {@link org.example.exception.AiNotFoundException}
 * before any SSE bytes are written); every failure past that point becomes a typed {@code error}
 * SSE event inside an otherwise-200 stream (PLAN.md §6).
 */
@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiChatRateLimitService rateLimitService;

    public AiChatController(AiChatService aiChatService, AiChatRateLimitService rateLimitService) {
        this.aiChatService = aiChatService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AiQuotaProtected
    public SseEmitter sendMessage(
            @PathVariable UUID id,
            @RequestBody SendMessageRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        String userSub = AiSecurityUtil.requireSub();
        rateLimitService.registerUser(userSub, AiSecurityUtil.currentUsername());
        String bearerToken = AiSecurityUtil.requireBearerToken();
        Set<String> callerRoles = AiSecurityUtil.currentRoleSet();
        return aiChatService.streamMessage(userSub, id, request.getContent(), acceptLanguage, bearerToken, callerRoles);
    }
}
