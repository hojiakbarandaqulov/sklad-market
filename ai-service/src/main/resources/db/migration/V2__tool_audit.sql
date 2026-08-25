CREATE TABLE tool_audit (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversation (id),
    message_id      uuid REFERENCES message (id),
    user_sub        varchar(255) NOT NULL,
    tool_name       varchar(255) NOT NULL,
    arguments       jsonb,
    result_status   varchar(16) NOT NULL,
    http_status     integer,
    latency_ms      integer,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_tool_audit_conversation_id ON tool_audit (conversation_id);
CREATE INDEX idx_tool_audit_user_sub ON tool_audit (user_sub);
