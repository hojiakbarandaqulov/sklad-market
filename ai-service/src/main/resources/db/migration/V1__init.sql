CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE conversation (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_sub    varchar(255) NOT NULL,
    user_role   varchar(64)  NOT NULL,
    title       varchar(255),
    locale      varchar(8),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);

CREATE INDEX idx_conversation_user_sub ON conversation (user_sub);

CREATE TABLE message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversation (id),
    role            varchar(16) NOT NULL,
    content         text NOT NULL,
    tool_name       varchar(255),
    tool_payload    jsonb,
    tokens_in       integer,
    tokens_out      integer,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_conversation_id ON message (conversation_id);

CREATE TABLE usage_ledger (
    id             bigserial PRIMARY KEY,
    user_sub       varchar(255) NOT NULL,
    day            date NOT NULL,
    tokens_in      bigint NOT NULL DEFAULT 0,
    tokens_out     bigint NOT NULL DEFAULT 0,
    request_count  integer NOT NULL DEFAULT 0,
    UNIQUE (user_sub, day)
);
