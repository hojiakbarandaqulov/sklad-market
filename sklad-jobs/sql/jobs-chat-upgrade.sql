-- Run in jobs PostgreSQL database before deploying. Existing tables must exist.
BEGIN;
ALTER TABLE job_application ADD COLUMN IF NOT EXISTS chat_thread_id bigint;
ALTER TABLE job_application ADD COLUMN IF NOT EXISTS chat_pending boolean NOT NULL DEFAULT false;
ALTER TABLE job_application ADD COLUMN IF NOT EXISTS chat_next_attempt_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_application_pending_chat ON job_application(chat_next_attempt_at,id)
    WHERE chat_pending = true AND chat_thread_id IS NULL AND deleted = false;
COMMIT;
-- Old applications are linked on POST /api/v1/applications/{id}/chat.
-- New applications automatically set chat_pending=true.