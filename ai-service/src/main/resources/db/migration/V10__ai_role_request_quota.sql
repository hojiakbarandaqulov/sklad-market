CREATE TABLE ai_role_request_quota (
    role_name              varchar(64) PRIMARY KEY,
    hourly_request_limit   integer NOT NULL,
    daily_request_limit    integer NOT NULL,
    updated_at             timestamptz NOT NULL DEFAULT now(),
    updated_by             varchar(255),
    CONSTRAINT chk_ai_role_quota_hourly
        CHECK (hourly_request_limit BETWEEN 0 AND 1000000),
    CONSTRAINT chk_ai_role_quota_daily
        CHECK (daily_request_limit BETWEEN 0 AND 1000000)
);

-- Role names are deliberately data, not an application enum. Adding a future entitlement such as
-- PREMIUM therefore requires only an admin PUT plus the corresponding live JWT realm role.
INSERT INTO ai_role_request_quota (role_name, hourly_request_limit, daily_request_limit)
VALUES ('USER', 30, 100),
       ('BUYER', 120, 500),
       ('SELLER', 180, 1000),
       ('ADMIN', 600, 5000),
       ('SUPER_ADMIN', 600, 5000);

CREATE TABLE ai_request_quota_usage (
    user_sub       varchar(255) NOT NULL,
    window_type    varchar(8) NOT NULL,
    window_start   timestamptz NOT NULL,
    request_count  integer NOT NULL DEFAULT 0,
    PRIMARY KEY (user_sub, window_type, window_start),
    CONSTRAINT chk_ai_request_quota_window_type
        CHECK (window_type IN ('HOUR', 'DAY')),
    CONSTRAINT chk_ai_request_quota_count
        CHECK (request_count >= 0)
);

CREATE INDEX idx_ai_request_quota_usage_window_start
    ON ai_request_quota_usage (window_start);
