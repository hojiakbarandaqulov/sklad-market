package org.example.service.impl;

import org.example.ai.LocaleNormalizer;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.AgentTool;
import org.example.dto.ConversationDto;
import org.example.dto.MessageDto;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.example.entity.Conversation;
import org.example.entity.Message;
import org.example.exception.AiNotFoundException;
import org.example.repository.ConversationRepository;
import org.example.repository.MessageRepository;
import org.example.security.ConversationRolePolicy;
import org.example.service.ConversationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_ACTIVE_CONVERSATIONS_PER_USER = 15;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ToolRegistry toolRegistry;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ToolRegistry toolRegistry) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.toolRegistry = toolRegistry;
    }

    @Override
    @Transactional
    public ConversationDto create(String userSub, String userRole, String title, String acceptLanguage) {
        Conversation conversation = new Conversation();
        conversation.setUserSub(userSub);
        conversation.setUserRole(userRole == null || userRole.isBlank() ? "USER" : userRole);
        conversation.setTitle(title == null || title.isBlank() ? null : title.trim());
        conversation.setLocale(LocaleNormalizer.normalize(acceptLanguage));
        Conversation saved = conversationRepository.save(conversation);
        conversationRepository.softDeleteOlderActiveConversations(
                userSub, MAX_ACTIVE_CONVERSATIONS_PER_USER, Instant.now());
        return toDto(saved);
    }

    @Override
    public PagedResponse<ConversationDto> list(String userSub, int page, int perPage) {
        validatePage(page, perPage);
        int boundedPerPage = Math.min(perPage, MAX_ACTIVE_CONVERSATIONS_PER_USER);
        Page<Conversation> result = conversationRepository.findByUserSubAndDeletedAtIsNullOrderByUpdatedAtDesc(
                userSub, PageRequest.of(page - 1, boundedPerPage));
        List<ConversationDto> items = result.getContent().stream().map(this::toDto).toList();
        return new PagedResponse<>(items, new PageMeta(
                result.getTotalElements(), page, boundedPerPage, result.getTotalPages()));
    }

    @Override
    public PagedResponse<MessageDto> getMessages(
            String userSub, UUID conversationId, int page, int perPage, Set<String> currentRoles) {
        validatePage(page, perPage);
        requireOwnedForRoles(userSub, conversationId, currentRoles);
        Page<Message> result = messageRepository.findByConversationIdOrderByCreatedAtAsc(
                conversationId, PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.ASC, "createdAt")));
        List<MessageDto> items = result.getContent().stream()
                .map(message -> toDto(message, currentRoles))
                .toList();
        return new PagedResponse<>(items, new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages()));
    }

    @Override
    @Transactional
    public void delete(String userSub, UUID conversationId) {
        Conversation conversation = requireOwned(userSub, conversationId);
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    @Override
    public Conversation requireOwned(String userSub, UUID conversationId) {
        return conversationRepository.findByIdAndUserSubAndDeletedAtIsNull(conversationId, userSub)
                .orElseThrow(() -> new AiNotFoundException("Conversation not found"));
    }

    @Override
    public Conversation requireOwnedForRoles(
            String userSub, UUID conversationId, Set<String> currentRoles) {
        Conversation conversation = requireOwned(userSub, conversationId);
        Set<String> roles = currentRoles == null ? Set.of() : currentRoles;
        ConversationRolePolicy.requireCurrentRoles(conversation, roles);
        for (String requiredRoles : messageRepository.findDistinctRequiredRolesByConversationId(conversationId)) {
            ConversationRolePolicy.requireCurrentRolesSnapshot(requiredRoles, roles);
        }
        for (String toolName : messageRepository
                .findDistinctSuccessfulLegacyToolNamesByConversationId(conversationId)) {
            AgentTool tool = toolRegistry.findRegistered(toolName).orElse(null);
            if (tool == null) throw new AiNotFoundException("Conversation not found");
            if (tool.allowedRoles() == null || tool.allowedRoles().isEmpty()) continue;
            Set<String> allowed = tool.allowedRoles();
            boolean adminAliases = allowed.equals(Set.of("ADMIN", "SUPER_ADMIN"));
            boolean permitted = adminAliases
                    ? toolRegistry.findAllowed(toolName, roles).isPresent()
                    : roles.containsAll(allowed);
            if (!permitted) throw new AiNotFoundException("Conversation not found");
        }
        return conversation;
    }

    private void validatePage(int page, int perPage) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("per_page must be between 1 and 100");
        }
    }

    private ConversationDto toDto(Conversation conversation) {
        return ConversationDto.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .locale(conversation.getLocale())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageDto toDto(Message message, Set<String> currentRoles) {
        String toolPayload = message.getToolPayload();
        if (message.getToolName() != null
                && toolRegistry.findAllowed(message.getToolName(), currentRoles == null ? Set.of() : currentRoles).isEmpty()) {
            // The caller still owns the conversation, but a revoked role must not keep granting
            // access to role-derived structured snapshots (seller leads, buyer drafts, etc.).
            toolPayload = null;
        }
        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole().wireValue())
                .content(message.getContent())
                .toolName(message.getToolName())
                .toolPayload(toolPayload)
                .tokensIn(message.getTokensIn())
                .tokensOut(message.getTokensOut())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
