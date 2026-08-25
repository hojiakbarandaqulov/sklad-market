-- Phase 5: catalog embedding index for semantic search + "similar products".
-- The `vector` extension is already created by V1 (CREATE EXTENSION IF NOT EXISTS vector).
-- Vectors are 768-dim (gemini-embedding-001, MRL-truncated then L2-renormalized — PLAN.md §2/§7 item 2),
-- persisted/queried via plain JDBC with CAST(? AS vector) (no pgvector Hibernate type on this stack — §7 item 5).

CREATE TABLE product_embedding (
    product_id   bigint PRIMARY KEY,          -- product-service product id (its own id space)
    slug         varchar(255) NOT NULL,       -- for building /product/<slug> links in results
    name         varchar(512) NOT NULL,
    category_id  bigint,
    region_id    bigint,
    price        numeric,
    currency     varchar(16),
    content_hash varchar(64)  NOT NULL,        -- SHA-256 of the embedded source fields; unchanged hash => skip re-embed
    embedding    vector(768)  NOT NULL,
    indexed_at   timestamptz  NOT NULL DEFAULT now()
);

-- Approximate-nearest-neighbour index for cosine distance (<=>). HNSW supports up to 2000 dims; 768 is fine.
-- Building it on an (initially) empty table is instant; it stays valid as rows are upserted.
CREATE INDEX idx_product_embedding_hnsw ON product_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_product_embedding_category ON product_embedding (category_id);

-- One row per indexer run: observability + the admin status endpoint read the latest row.
CREATE TABLE index_state (
    id               bigserial PRIMARY KEY,
    last_run_at      timestamptz NOT NULL DEFAULT now(),
    last_status      varchar(32) NOT NULL,        -- SUCCESS | PARTIAL | FAILURE | SKIPPED
    products_indexed integer     NOT NULL DEFAULT 0,
    notes            text
);
