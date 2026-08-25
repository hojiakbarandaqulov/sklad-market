CREATE TABLE action_draft (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversation (id),
    user_sub        varchar(255) NOT NULL,
    type            varchar(32) NOT NULL,
    payload         jsonb NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'DRAFT',
    idempotency_key varchar(64) NOT NULL UNIQUE,
    lead_id         bigint,
    created_at      timestamptz NOT NULL DEFAULT now(),
    confirmed_at    timestamptz,
    expires_at      timestamptz NOT NULL
);

CREATE INDEX idx_action_draft_user_sub ON action_draft (user_sub);
CREATE INDEX idx_action_draft_conversation_id ON action_draft (conversation_id);
