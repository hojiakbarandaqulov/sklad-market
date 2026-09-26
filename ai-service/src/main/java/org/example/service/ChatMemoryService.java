package org.example.service;

import org.example.exception.AiNotFoundException;
import org.example.exception.ChatConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** AI-only lifecycle operations; never updates catalog, orders, leads, or action drafts. */
@Service
public class ChatMemoryService {
    private final JdbcTemplate jdbc;

    public ChatMemoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void claim(String userSub, UUID conversationId, UUID requestId, String content) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        lockOwned(userSub, conversationId);
        String hash = hash(content);
        var previous = jdbc.queryForList("""
                SELECT content_hash, status, lease_until > now() AS active FROM ai_chat_turn
                WHERE conversation_id = ? AND request_id = ?
                """, conversationId, requestId);
        if (!previous.isEmpty()) {
            if (!hash.equals(previous.get(0).get("content_hash")))
                throw new IllegalArgumentException("Request ID was already used for another message");
            if ("RUNNING".equals(previous.get(0).get("status")) && Boolean.TRUE.equals(previous.get(0).get("active")))
                throw new ChatConflictException("conversation_busy", "A reply is already being generated in this chat.");
            throw new ChatConflictException("request_recorded",
                    "This request was already accepted. Reload the conversation before sending again.");
        }
        Boolean running = jdbc.queryForObject("""
                SELECT COALESCE(active_until > now(), false) FROM conversation WHERE id = ?
                """, Boolean.class, conversationId);
        if (Boolean.TRUE.equals(running))
            throw new ChatConflictException("conversation_busy", "A reply is already being generated in this chat.");
        jdbc.update("""
                INSERT INTO ai_chat_turn (conversation_id, request_id, content_hash, status, lease_until)
                VALUES (?, ?, ?, 'RUNNING', now() + interval '2 minutes')
                """, conversationId, requestId, hash);
        jdbc.update("UPDATE conversation SET active_request_id = ?, active_until = now() + interval '2 minutes' WHERE id = ?",
                requestId, conversationId);
    }

    @Transactional
    public boolean renew(UUID conversationId, UUID requestId) {
        int held = jdbc.update("""
                UPDATE conversation SET active_until = now() + interval '2 minutes'
                WHERE id = ? AND active_request_id = ? AND active_until > now()
                """, conversationId, requestId);
        if (held == 0) return false;
        return jdbc.update("""
                UPDATE ai_chat_turn SET lease_until = now() + interval '2 minutes'
                WHERE conversation_id = ? AND request_id = ? AND status = 'RUNNING'
                  AND lease_until > now()
                """, conversationId, requestId) == 1;
    }

    @Transactional
    public void finish(UUID conversationId, UUID requestId) {
        jdbc.update("UPDATE conversation SET active_request_id = NULL, active_until = NULL WHERE id = ? AND active_request_id = ?",
                conversationId, requestId);
        jdbc.update("""
                UPDATE ai_chat_turn SET status = 'FINISHED', lease_until = now()
                WHERE conversation_id = ? AND request_id = ?
                """, conversationId, requestId);
    }

    @Transactional
    public void retainRecent(String userSub, UUID conversationId) {
        // Serialize session activation for this owner, including concurrent tabs/instances.
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, userSub);
        lockOwned(userSub, conversationId);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COALESCE(active_until > now(), false) FROM conversation WHERE id = ?", Boolean.class, conversationId))) return;
        trimMessages(conversationId);
        jdbc.update("""
                UPDATE conversation SET deleted_at = now() WHERE id IN (
                    SELECT c.id FROM conversation c
                    WHERE c.user_sub = ? AND c.deleted_at IS NULL
                      AND EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id)
                    ORDER BY c.updated_at DESC, c.created_at DESC, c.id DESC OFFSET 15)
                  AND id <> ?
                  AND (active_until IS NULL OR active_until <= now())
                  AND NOT EXISTS (SELECT 1 FROM action_draft d WHERE d.conversation_id = conversation.id
                    AND d.status = 'DRAFT' AND d.expires_at > now())
                """, userSub, conversationId);
    }

    public void trimMessages(UUID conversationId) {
        preserveAccess(conversationId);
        jdbc.update("""
                UPDATE message SET archived_at = now()
                WHERE conversation_id = ? AND archived_at IS NULL AND created_at < (
                    SELECT created_at FROM message WHERE conversation_id = ? AND role = 'user'
                    ORDER BY created_at DESC, id DESC OFFSET 14 LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM action_draft WHERE conversation_id = ?
                    AND status = 'DRAFT' AND expires_at > now())
                """, conversationId, conversationId, conversationId);
    }

    public void preserveAccess(UUID conversationId) {
        jdbc.update("""
                INSERT INTO ai_conversation_access (conversation_id, kind, requirement)
                SELECT DISTINCT conversation_id, 'ROLES', required_roles FROM message
                WHERE conversation_id = ? AND required_roles IS NOT NULL AND archived_at IS NULL
                ON CONFLICT DO NOTHING
                """, conversationId);
        jdbc.update("""
                INSERT INTO ai_conversation_access (conversation_id, kind, requirement)
                SELECT DISTINCT conversation_id, 'LEGACY_TOOL', tool_name FROM message
                WHERE conversation_id = ? AND required_roles IS NULL AND tool_name IS NOT NULL AND archived_at IS NULL
                  AND content = tool_name || ' completed'
                ON CONFLICT DO NOTHING
                """, conversationId);
    }

    private void lockOwned(String userSub, UUID conversationId) {
        var ids = jdbc.queryForList("""
                SELECT id FROM conversation WHERE id = ? AND user_sub = ? AND deleted_at IS NULL FOR UPDATE
                """, UUID.class, conversationId, userSub);
        if (ids.isEmpty()) throw new AiNotFoundException("Conversation not found");
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content.trim()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
