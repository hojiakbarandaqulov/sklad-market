package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Map;

/** Bounded AI history cleanup. Drafts, business records and tool audit trails are not deleted. */
@Service
public class ChatHistoryMaintenance {
    private final JdbcTemplate jdbc;
    private final ChatMemoryService memory;
    private final boolean enabled;
    private final int recoveryDays;

    public ChatHistoryMaintenance(JdbcTemplate jdbc, ChatMemoryService memory,
            @Value("${ai.history.cleanup-enabled:false}") boolean enabled,
            @Value("${ai.history.recovery-days:30}") int recoveryDays) {
        this.jdbc = jdbc;
        this.memory = memory;
        this.enabled = enabled;
        this.recoveryDays = Math.max(30, recoveryDays);
    }

    @Scheduled(cron = "${ai.history.cleanup-cron:0 43 * * * *}", scheduler = "aiHistoryScheduler")
    @Transactional
    public int clean() {
        if (!enabled) return 0;
        jdbc.execute("SET LOCAL statement_timeout = '10s'");
        var candidates = jdbc.queryForList("""
                SELECT c.id FROM conversation c WHERE c.purged_at IS NULL
                  AND (c.active_until IS NULL OR c.active_until <= now())
                  AND NOT EXISTS (SELECT 1 FROM action_draft d WHERE d.conversation_id = c.id
                    AND d.status = 'DRAFT' AND d.expires_at > now())
                  AND (c.deleted_at < now() - make_interval(days => ?)
                    OR (c.created_at < now() - interval '24 hours'
                        AND NOT EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id))
                    OR EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id
                        AND m.archived_at < now() - make_interval(days => ?))
                    OR EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id
                        AND m.role = 'user' AND m.archived_at IS NULL OFFSET 15))
                ORDER BY c.updated_at LIMIT 20 FOR UPDATE SKIP LOCKED
                """, UUID.class, recoveryDays, recoveryDays);
        int removed = 0;
        for (UUID id : candidates) {
            // Recheck after acquiring the row lock: a turn/draft may have started while selecting candidates.
            Boolean protectedChat = jdbc.queryForObject("""
                    SELECT COALESCE(active_until > now(), false)
                        OR EXISTS (SELECT 1 FROM action_draft d WHERE d.conversation_id = c.id
                            AND d.status = 'DRAFT' AND d.expires_at > now())
                    FROM conversation c WHERE c.id = ?
                    """, Boolean.class, id);
            if (Boolean.TRUE.equals(protectedChat)) continue;
            memory.trimMessages(id);
            // Keep audit evidence, but detach references before deleting expired chat text.
            var messages = jdbc.queryForList("""
                    SELECT m.id FROM message m JOIN conversation c ON c.id = m.conversation_id
                    WHERE c.id = ? AND (c.deleted_at < now() - make_interval(days => ?)
                        OR m.archived_at < now() - make_interval(days => ?))
                    ORDER BY m.created_at LIMIT 1000
                    """, UUID.class, id, recoveryDays, recoveryDays);
            if (!messages.isEmpty()) {
                var named = new NamedParameterJdbcTemplate(jdbc);
                named.update("UPDATE tool_audit SET message_id = NULL WHERE message_id IN (:ids)", Map.of("ids", messages));
                removed += named.update("DELETE FROM message WHERE id IN (:ids)", Map.of("ids", messages));
            }
            jdbc.update("""
                    DELETE FROM ai_chat_turn WHERE conversation_id = ? AND lease_until < now()
                      AND created_at < now() - make_interval(days => ?)
                    """, id, recoveryDays);
            Boolean empty = jdbc.queryForObject(
                    "SELECT NOT EXISTS (SELECT 1 FROM message WHERE conversation_id = ?)", Boolean.class, id);
            if (Boolean.TRUE.equals(empty)) {
                // Referenced sessions remain tiny tombstones so confirmations/audits keep valid FKs.
                jdbc.update("""
                        UPDATE conversation SET title = NULL, deleted_at = COALESCE(deleted_at, now()),
                            purged_at = now() WHERE id = ?
                        """, id);
                Boolean referenced = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM action_draft WHERE conversation_id = ?)
                            OR EXISTS (SELECT 1 FROM tool_audit WHERE conversation_id = ?)
                        """, Boolean.class, id, id);
                if (!Boolean.TRUE.equals(referenced)) {
                    jdbc.update("DELETE FROM ai_conversation_access WHERE conversation_id = ?", id);
                    jdbc.update("DELETE FROM ai_chat_turn WHERE conversation_id = ?", id);
                    jdbc.update("DELETE FROM conversation WHERE id = ?", id);
                }
            }
        }
        return removed;
    }
}
