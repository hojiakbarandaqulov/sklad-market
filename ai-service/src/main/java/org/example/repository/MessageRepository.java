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

    @Query("select m from Message m where m.conversationId = :conversationId and m.archivedAt is null order by m.createdAt, m.id")
    Page<Message> findByConversationIdOrderByCreatedAtAsc(@Param("conversationId") UUID conversationId, Pageable pageable);

    @Query(value = """
            SELECT recent.* FROM (SELECT m.* FROM message m WHERE m.conversation_id = :conversationId AND m.archived_at IS NULL
              AND (
              EXISTS (SELECT 1 FROM action_draft d WHERE d.conversation_id = :conversationId
                AND d.status = 'DRAFT' AND d.expires_at > now()
                AND m.tool_payload->'draftRef'->>'draftId' = d.id::text)
              OR
              m.created_at >= COALESCE((SELECT created_at FROM message
                  WHERE conversation_id = :conversationId AND role = 'user' AND archived_at IS NULL
                  ORDER BY created_at DESC, id DESC OFFSET 14 LIMIT 1), '-infinity'::timestamptz))
            ORDER BY m.created_at DESC, m.id DESC LIMIT 1000) recent ORDER BY recent.created_at, recent.id
            """, nativeQuery = true)
    List<Message> findRecentExchanges(@Param("conversationId") UUID conversationId);

    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @Query("select m from Message m where m.conversationId = :conversationId and m.archivedAt is null "
            + "and m.role in :roles order by m.createdAt desc, m.id desc")
    List<Message> findByConversationIdAndRoleInOrderByCreatedAtDesc(
            @Param("conversationId") UUID conversationId, @Param("roles") Collection<MessageRole> roles, Pageable pageable);

    long countByConversationId(UUID conversationId);

    @Query(value = "SELECT DISTINCT required_roles FROM message WHERE conversation_id = :conversationId AND required_roles IS NOT NULL "
            + "UNION SELECT requirement FROM ai_conversation_access WHERE conversation_id = :conversationId AND kind = 'ROLES'", nativeQuery = true)
    List<String> findDistinctRequiredRolesByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * Successful pre-V7 tools have no exact role snapshot. Failed hallucinated/denied calls are
     * intentionally excluded so they can never brick a conversation.
     */
    @Query(value = "SELECT DISTINCT tool_name FROM message WHERE conversation_id = :conversationId "
            + "AND tool_name IS NOT NULL AND required_roles IS NULL AND content = tool_name || ' completed' "
            + "UNION SELECT requirement FROM ai_conversation_access WHERE conversation_id = :conversationId AND kind = 'LEGACY_TOOL'", nativeQuery = true)
    List<String> findDistinctSuccessfulLegacyToolNamesByConversationId(
            @Param("conversationId") UUID conversationId);
}
