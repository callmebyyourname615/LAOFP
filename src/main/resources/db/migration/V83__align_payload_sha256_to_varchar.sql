-- Phase 53B: align SHA-256 payload columns with the JPA VARCHAR(64) mapping.
--
-- IMPORTANT:
--   * V47 and V51 are intentionally immutable because they may already be recorded
--     in flyway_schema_history in deployed environments.
--   * This migration preserves existing values, removes only CHAR padding, and
--     fails closed if any row is not a 64-character hexadecimal SHA-256 digest.
--   * The explicit lock timeout prevents an unattended rollout from waiting
--     indefinitely behind long-running transactions.

SET LOCAL lock_timeout = '15s';
SET LOCAL statement_timeout = '5min';

DO $phase53b_preconditions$
BEGIN
    IF to_regclass('configuration_change_requests') IS NULL THEN
        RAISE EXCEPTION 'V83 precondition failed: configuration_change_requests table is missing';
    END IF;

    IF to_regclass('outbox_dead_letters') IS NULL THEN
        RAISE EXCEPTION 'V83 precondition failed: outbox_dead_letters table is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'configuration_change_requests'
          AND column_name = 'payload_sha256'
    ) THEN
        RAISE EXCEPTION 'V83 precondition failed: configuration_change_requests.payload_sha256 is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'outbox_dead_letters'
          AND column_name = 'payload_sha256'
    ) THEN
        RAISE EXCEPTION 'V83 precondition failed: outbox_dead_letters.payload_sha256 is missing';
    END IF;
END
$phase53b_preconditions$;

-- Acquire both table locks in one deterministic statement to avoid lock-order
-- inversions between concurrent deployment processes. The lock is released when
-- Flyway commits or rolls back this migration transaction.
LOCK TABLE configuration_change_requests, outbox_dead_letters
    IN ACCESS EXCLUSIVE MODE;

DO $phase53b_data_validation$
DECLARE
    invalid_configuration_hashes BIGINT;
    invalid_dead_letter_hashes BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO invalid_configuration_hashes
      FROM configuration_change_requests
     WHERE payload_sha256 IS NULL
        OR rtrim(payload_sha256) !~ '^[0-9A-Fa-f]{64}$';

    SELECT COUNT(*)
      INTO invalid_dead_letter_hashes
      FROM outbox_dead_letters
     WHERE payload_sha256 IS NULL
        OR rtrim(payload_sha256) !~ '^[0-9A-Fa-f]{64}$';

    IF invalid_configuration_hashes > 0 OR invalid_dead_letter_hashes > 0 THEN
        RAISE EXCEPTION 'V83 refused to convert invalid payload SHA-256 values'
            USING DETAIL = format(
                'configuration_change_requests invalid rows=%s; outbox_dead_letters invalid rows=%s',
                invalid_configuration_hashes,
                invalid_dead_letter_hashes
            ),
            HINT = 'Quarantine and repair invalid rows before retrying the migration; values are not printed to avoid data disclosure.';
    END IF;
END
$phase53b_data_validation$;

ALTER TABLE configuration_change_requests
    ALTER COLUMN payload_sha256 TYPE VARCHAR(64)
    USING rtrim(payload_sha256)::VARCHAR(64);

ALTER TABLE outbox_dead_letters
    ALTER COLUMN payload_sha256 TYPE VARCHAR(64)
    USING rtrim(payload_sha256)::VARCHAR(64);

-- NOT VALID followed by VALIDATE makes the validation intent explicit and leaves
-- a clear audit trail in PostgreSQL metadata. The tables are already locked by
-- this migration, so no invalid row can race between preflight and validation.
ALTER TABLE configuration_change_requests
    ADD CONSTRAINT ck_config_change_payload_sha256
    CHECK (payload_sha256 ~ '^[0-9A-Fa-f]{64}$') NOT VALID;

ALTER TABLE configuration_change_requests
    VALIDATE CONSTRAINT ck_config_change_payload_sha256;

ALTER TABLE outbox_dead_letters
    ADD CONSTRAINT ck_outbox_dlq_payload_sha256
    CHECK (payload_sha256 ~ '^[0-9A-Fa-f]{64}$') NOT VALID;

ALTER TABLE outbox_dead_letters
    VALIDATE CONSTRAINT ck_outbox_dlq_payload_sha256;

COMMENT ON COLUMN configuration_change_requests.payload_sha256 IS
    '64-character hexadecimal SHA-256 digest of the canonical configuration change payload';

COMMENT ON COLUMN outbox_dead_letters.payload_sha256 IS
    '64-character hexadecimal SHA-256 digest of the serialized dead-letter payload';

DO $phase53b_postconditions$
DECLARE
    aligned_column_count INTEGER;
    validated_constraint_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO aligned_column_count
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND column_name = 'payload_sha256'
       AND table_name IN ('configuration_change_requests', 'outbox_dead_letters')
       AND data_type = 'character varying'
       AND character_maximum_length = 64
       AND is_nullable = 'NO';

    IF aligned_column_count <> 2 THEN
        RAISE EXCEPTION 'V83 postcondition failed: expected two non-null VARCHAR(64) payload_sha256 columns, found %',
            aligned_column_count;
    END IF;

    SELECT COUNT(*)
      INTO validated_constraint_count
      FROM pg_constraint constraint_metadata
      JOIN pg_class table_metadata
        ON table_metadata.oid = constraint_metadata.conrelid
      JOIN pg_namespace schema_metadata
        ON schema_metadata.oid = table_metadata.relnamespace
     WHERE schema_metadata.nspname = current_schema()
       AND constraint_metadata.conname IN (
           'ck_config_change_payload_sha256',
           'ck_outbox_dlq_payload_sha256'
       )
       AND constraint_metadata.contype = 'c'
       AND constraint_metadata.convalidated;

    IF validated_constraint_count <> 2 THEN
        RAISE EXCEPTION 'V83 postcondition failed: expected two validated payload SHA-256 constraints, found %',
            validated_constraint_count;
    END IF;
END
$phase53b_postconditions$;
