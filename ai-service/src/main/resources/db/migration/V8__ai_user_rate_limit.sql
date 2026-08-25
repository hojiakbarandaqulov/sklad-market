CREATE TABLE ai_user_rate_limit (
    user_sub             varchar(255) PRIMARY KEY,
    username             varchar(255),
    requests_per_minute  integer,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_user_rate_limit_rpm
        CHECK (requests_per_minute IS NULL OR requests_per_minute BETWEEN 0 AND 10000)
);

INSERT INTO ai_user_rate_limit (user_sub)
SELECT DISTINCT user_sub
FROM conversation
ON CONFLICT (user_sub) DO NOTHING;
