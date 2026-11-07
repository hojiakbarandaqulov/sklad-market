-- Keep existing leads and extend Hibernate's old status CHECK for ACCEPTED.
ALTER TABLE IF EXISTS lead ADD COLUMN IF NOT EXISTS accepted_order_id BIGINT;
ALTER TABLE IF EXISTS lead_item ALTER COLUMN quantity TYPE NUMERIC(19,3) USING quantity::numeric;
DO $migration$
DECLARE
    old_check RECORD;
BEGIN
    -- Fresh installs still let Hibernate create the module's initial schema.
    IF to_regclass('lead') IS NULL THEN RETURN; END IF;
    FOR old_check IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='status'
        WHERE c.conrelid='lead'::regclass AND c.contype='c' AND c.conkey=ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE lead DROP CONSTRAINT %I',old_check.conname);
    END LOOP;
    ALTER TABLE lead ADD CONSTRAINT ck_lead_status CHECK (status IN ('NEW','VIEWED','CONTACTED','CLOSED','CANCELED','ACCEPTED'));
END;
$migration$;
