CREATE TABLE buying_intent (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_sub       varchar(255) NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'DRAFT',
    category        varchar(160) NOT NULL,
    region          varchar(160),
    need_text       varchar(2000) NOT NULL,
    quantity        numeric(19, 3),
    quantity_unit   varchar(32),
    budget_min      numeric(19, 2),
    budget_max      numeric(19, 2),
    currency        varchar(3) NOT NULL DEFAULT 'UZS',
    expires_at      timestamptz NOT NULL,
    published_at    timestamptz,
    publication_consent_at timestamptz,
    closed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT chk_buying_intent_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'EXPIRED')),
    CONSTRAINT chk_buying_intent_quantity
        CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT chk_buying_intent_budget_min
        CHECK (budget_min IS NULL OR budget_min >= 0),
    CONSTRAINT chk_buying_intent_budget_max
        CHECK (budget_max IS NULL OR budget_max >= 0),
    CONSTRAINT chk_buying_intent_budget_range
        CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_max >= budget_min),
    CONSTRAINT chk_buying_intent_publication_consent
        CHECK (status <> 'PUBLISHED'
            OR (published_at IS NOT NULL AND publication_consent_at IS NOT NULL))
);

CREATE INDEX idx_buying_intent_owner_created
    ON buying_intent (owner_sub, created_at DESC);

CREATE INDEX idx_buying_intent_owner_active
    ON buying_intent (owner_sub, status, expires_at)
    WHERE status IN ('DRAFT', 'PUBLISHED');

CREATE INDEX idx_buying_intent_published_expiry
    ON buying_intent (expires_at, category, region)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_buying_intent_retention
    ON buying_intent (status, updated_at)
    WHERE status IN ('CLOSED', 'EXPIRED');

-- Privacy boundary: owner_sub is never selected into seller projections and raw contact columns do
-- not exist in this AI-owned table. category/region/need_text are buyer-authored and become visible
-- to sellers after explicit consent; best-effort screening cannot guarantee that free text is anonymous.
-- Contact exchange must use the platform's authorized lead/chat flows.
