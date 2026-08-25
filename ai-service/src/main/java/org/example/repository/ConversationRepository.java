package org.example.repository;

import org.example.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Page<Conversation> findByUserSubAndDeletedAtIsNullOrderByUpdatedAtDesc(String userSub, Pageable pageable);

    Optional<Conversation> findByIdAndUserSubAndDeletedAtIsNull(UUID id, String userSub);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE conversation
               SET deleted_at = :deletedAt
             WHERE id IN (
                 SELECT id
                   FROM conversation
                  WHERE user_sub = :userSub
                    AND deleted_at IS NULL
                  ORDER BY updated_at DESC, created_at DESC, id DESC
                 OFFSET :keepCount
             )
            """, nativeQuery = true)
    void softDeleteOlderActiveConversations(
            @Param("userSub") String userSub,
            @Param("keepCount") int keepCount,
            @Param("deletedAt") Instant deletedAt);
}
