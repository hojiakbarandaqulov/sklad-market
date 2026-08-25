package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ApiResponse;
import org.example.dto.ConversationDto;
import org.example.dto.CreateConversationRequest;
import org.example.dto.MessageDto;
import org.example.dto.PagedResponse;
import org.example.security.AiSecurityUtil;
import org.example.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai/conversations")
public class ConversationController {

    private static final Set<String> AI_DATA_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "BUYER", "SELLER");

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationDto> create(
            @Valid @RequestBody(required = false) CreateConversationRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String title = request == null ? null : request.getTitle();
        String userSub = AiSecurityUtil.requireSub();
        String userRole = roleSnapshot();
        return ApiResponse.successResponse(conversationService.create(userSub, userRole, title, acceptLanguage));
    }

    @GetMapping
    public ApiResponse<PagedResponse<ConversationDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        return ApiResponse.successResponse(conversationService.list(AiSecurityUtil.requireSub(), page, perPage));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PagedResponse<MessageDto>> getMessages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        return ApiResponse.successResponse(conversationService.getMessages(
                AiSecurityUtil.requireSub(), id, page, perPage, AiSecurityUtil.currentRoleSet()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        conversationService.delete(AiSecurityUtil.requireSub(), id);
        return ApiResponse.successResponse();
    }

    private String roleSnapshot() {
        String roles = AiSecurityUtil.currentRoleSet().stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .filter(AI_DATA_ROLES::contains)
                .sorted()
                .collect(Collectors.joining(","));
        return roles.isBlank() ? "USER" : roles;
    }
}
