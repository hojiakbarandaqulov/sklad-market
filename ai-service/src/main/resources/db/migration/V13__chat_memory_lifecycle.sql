-- Additive AI-only migration. No existing history is deleted during deployment.
ALTER TABLE conversation ADD COLUMN client_request_id uuid;
ALTER TABLE conversation ADD COLUMN purged_at timestamptz;
ALTER TABLE conversation ADD COLUMN active_request_id uuid;
ALTER TABLE conversation ADD COLUMN active_until timestamptz;
CREATE UNIQUE INDEX idx_conversation_create_request
    ON conversation (user_sub, client_request_id) WHERE client_request_id IS NOT NULL;
ALTER TABLE message ADD COLUMN archived_at timestamptz;
CREATE INDEX idx_message_recent ON message (conversation_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;
CREATE INDEX idx_message_archive ON message (archived_at) WHERE archived_at IS NOT NULL;
CREATE INDEX idx_message_recent_user ON message (conversation_id, created_at DESC, id DESC)
    WHERE role = 'user' AND archived_at IS NULL;
CREATE INDEX idx_tool_audit_message_id ON tool_audit (message_id) WHERE message_id IS NOT NULL;

-- A short-lived, database-backed lease works across application instances/tabs.
CREATE TABLE ai_chat_turn (
    conversation_id uuid NOT NULL REFERENCES conversation(id),
    request_id uuid NOT NULL,
    content_hash varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    lease_until timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, request_id)
);
CREATE INDEX idx_ai_chat_turn_active ON ai_chat_turn (conversation_id, lease_until)
    WHERE status = 'RUNNING';

-- Trimming history must never erase role restrictions from earlier sensitive tool results.
CREATE TABLE ai_conversation_access (
    conversation_id uuid NOT NULL REFERENCES conversation(id),
    kind varchar(16) NOT NULL,
    requirement varchar(255) NOT NULL,
    PRIMARY KEY (conversation_id, kind, requirement)
);
