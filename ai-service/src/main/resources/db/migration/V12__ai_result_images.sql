-- Public card media cached alongside the AI-owned search projections. Both columns are nullable so
-- existing rows remain valid until the normal product/company index refresh backfills them.
ALTER TABLE product_embedding
    ADD COLUMN IF NOT EXISTS image_url text;

ALTER TABLE company_embedding
    ADD COLUMN IF NOT EXISTS logo_url text;
