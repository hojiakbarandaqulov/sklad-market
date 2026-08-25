package org.example.service;

import org.example.dto.ConversationDto;
import org.example.dto.MessageDto;
import org.example.dto.PagedResponse;
import org.example.entity.Conversation;

import java.util.UUID;
import java.util.Set;

public interface ConversationService {

    ConversationDto create(String userSub, String userRole, String title, String acceptLanguage);

    PagedResponse<ConversationDto> list(String userSub, int page, int perPage);

    PagedResponse<MessageDto> getMessages(
            String userSub, UUID conversationId, int page, int perPage, Set<String> currentRoles);

    void delete(String userSub, UUID conversationId);

    /** Owner-scoped lookup for internal callers (e.g. the SSE chat turn) that need the entity itself. */
    Conversation requireOwned(String userSub, UUID conversationId);

    /** Owner lookup plus current-role authorization for all tool-derived history in the conversation. */
    Conversation requireOwnedForRoles(String userSub, UUID conversationId, Set<String> currentRoles);
}
