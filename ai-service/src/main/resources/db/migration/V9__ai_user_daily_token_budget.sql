ALTER TABLE ai_user_rate_limit
    ADD COLUMN daily_token_budget bigint;

ALTER TABLE ai_user_rate_limit
    ADD CONSTRAINT chk_ai_user_daily_token_budget
        CHECK (daily_token_budget IS NULL OR daily_token_budget BETWEEN 0 AND 100000000);
