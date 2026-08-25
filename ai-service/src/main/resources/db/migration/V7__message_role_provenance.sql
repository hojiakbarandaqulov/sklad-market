ALTER TABLE message
    ADD COLUMN required_roles varchar(64);

CREATE INDEX idx_message_conversation_required_roles
    ON message (conversation_id, required_roles)
    WHERE required_roles IS NOT NULL;

COMMENT ON COLUMN message.required_roles IS
    'Comma-separated live AI role set required to replay this tool-derived message; null for public/legacy rows';
