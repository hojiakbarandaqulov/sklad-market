-- Isolated from registered users, conversations, orders and role quotas.
CREATE TABLE ai_guest_policy (
    id smallint PRIMARY KEY CHECK (id = 1),
    message_limit integer NOT NULL DEFAULT 5 CHECK (message_limit BETWEEN 0 AND 20),
    ip_salt uuid NOT NULL,
    updated_by varchar(255),
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO ai_guest_policy (id, ip_salt) VALUES (1, gen_random_uuid());

CREATE TABLE ai_guest_trial (
    token_hash varchar(64) PRIMARY KEY,
    used integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ai_guest_turn (
    token_hash varchar(64) NOT NULL REFERENCES ai_guest_trial(token_hash),
    request_id uuid NOT NULL,
    content_hash varchar(64) NOT NULL,
    prompt text,
    response_json text,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (token_hash, request_id)
);
CREATE INDEX ai_guest_turn_recent ON ai_guest_turn(token_hash, created_at DESC);
CREATE INDEX ai_guest_turn_cleanup ON ai_guest_turn(created_at) WHERE prompt IS NOT NULL OR response_json IS NOT NULL;
CREATE TABLE ai_guest_rate_window (
    scope varchar(80) NOT NULL,
    window_start timestamptz NOT NULL,
    used integer NOT NULL,
    PRIMARY KEY(scope, window_start)
);
