-- AI-owned supplier/company capability index. Public contact values deliberately remain outside
-- this database and are hydrated from company-service only for shortlisted results.
CREATE TABLE company_embedding (
    company_id          bigint PRIMARY KEY,
    slug                varchar(255) NOT NULL,
    name                varchar(512) NOT NULL,
    verification_status varchar(64),
    category_ids        bigint[] NOT NULL DEFAULT '{}',
    region_ids          bigint[] NOT NULL DEFAULT '{}',
    product_count       integer NOT NULL DEFAULT 0,
    min_price           numeric,
    max_price           numeric,
    content_hash        varchar(64) NOT NULL,
    embedding           vector(768) NOT NULL,
    indexed_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_company_embedding_hnsw
    ON company_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_company_embedding_categories ON company_embedding USING gin (category_ids);
CREATE INDEX idx_company_embedding_regions ON company_embedding USING gin (region_ids);
CREATE INDEX idx_company_embedding_verification ON company_embedding (verification_status);

CREATE TABLE business_index_state (
    id                bigserial PRIMARY KEY,
    last_run_at       timestamptz NOT NULL DEFAULT now(),
    last_status       varchar(32) NOT NULL,
    companies_indexed integer NOT NULL DEFAULT 0,
    notes             text
);

CREATE INDEX idx_business_index_state_latest
    ON business_index_state (last_run_at DESC, id DESC);
