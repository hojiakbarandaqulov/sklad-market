package org.example.repository;

import org.example.entity.Message;
import org.example.entity.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<Message> findByConversationIdAndRoleInOrderByCreatedAtDesc(
            UUID conversationId, Collection<MessageRole> roles, Pageable pageable);

    long countByConversationId(UUID conversationId);

    @Query("select distinct message.requiredRoles from Message message "
            + "where message.conversationId = :conversationId and message.requiredRoles is not null")
    List<String> findDistinctRequiredRolesByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * Successful pre-V7 tools have no exact role snapshot. Failed hallucinated/denied calls are
     * intentionally excluded so they can never brick a conversation.
     */
    @Query("select distinct message.toolName from Message message "
            + "where message.conversationId = :conversationId "
            + "and message.toolName is not null and message.requiredRoles is null "
            + "and message.content = concat(message.toolName, ' completed')")
    List<String> findDistinctSuccessfulLegacyToolNamesByConversationId(
            @Param("conversationId") UUID conversationId);
}
