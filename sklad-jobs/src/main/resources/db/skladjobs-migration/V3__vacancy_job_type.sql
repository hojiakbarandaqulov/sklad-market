-- Keep existing rows unclassified until the owner selects the correct type.
-- Nullable for compatibility with existing records and older clients.
ALTER TABLE vacancy ADD COLUMN IF NOT EXISTS job_type VARCHAR(32);
ALTER TABLE vacancy ADD CONSTRAINT chk_vacancy_job_type
    CHECK (job_type IS NULL OR job_type IN ('PERMANENT', 'TEMPORARY'));
CREATE INDEX IF NOT EXISTS idx_vacancy_job_type ON vacancy(job_type);
