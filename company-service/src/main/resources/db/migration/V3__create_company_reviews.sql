CREATE TABLE IF NOT EXISTS company_reviews (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT,
    created_date TIMESTAMP,
    modified_date TIMESTAMP,
    created_by BIGINT,
    modified_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_company_reviews_company
        FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uq_company_reviews_company_buyer
        UNIQUE (company_id, buyer_id),
    CONSTRAINT chk_company_reviews_rating
        CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_company_reviews_company_active_created
    ON company_reviews (company_id, deleted, created_date DESC);
