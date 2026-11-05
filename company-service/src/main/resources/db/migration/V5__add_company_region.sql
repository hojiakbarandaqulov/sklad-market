ALTER TABLE company ADD COLUMN IF NOT EXISTS region_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_company_region_id ON company (region_id);
