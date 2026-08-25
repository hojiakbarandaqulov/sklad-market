WITH ranked_conversations AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY user_sub
               ORDER BY updated_at DESC, created_at DESC, id DESC
           ) AS position
    FROM conversation
    WHERE deleted_at IS NULL
)
UPDATE conversation AS conversation_to_archive
SET deleted_at = now()
FROM ranked_conversations
WHERE conversation_to_archive.id = ranked_conversations.id
  AND ranked_conversations.position > 15;

CREATE INDEX IF NOT EXISTS idx_conversation_active_history
    ON conversation (user_sub, updated_at DESC)
    WHERE deleted_at IS NULL;
