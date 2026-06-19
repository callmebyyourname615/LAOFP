-- ============================================================
-- V45 · Provider-backed sanctions imports
-- Adds stable provider identifiers, normalized aliases, import audit history,
-- and a batch-scoped staging table used for atomic snapshot replacement.
-- ============================================================

ALTER TABLE sanctions_lists
    ADD COLUMN provider_uid VARCHAR(220),
    ADD COLUMN normalized_name VARCHAR(500),
    ADD COLUMN aliases JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN aliases_normalized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN content_hash CHAR(64),
    ADD COLUMN first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE sanctions_lists
SET provider_uid = list_type || ':LEGACY:' || list_id,
    normalized_name = trim(regexp_replace(lower(entity_name), '[^[:alnum:]]+', ' ', 'g')),
    content_hash = encode(sha256(convert_to(list_type || ':' || entity_name || ':' || list_id, 'UTF8')), 'hex')
WHERE provider_uid IS NULL;

ALTER TABLE sanctions_lists
    ALTER COLUMN provider_uid SET NOT NULL,
    ALTER COLUMN normalized_name SET NOT NULL,
    ALTER COLUMN content_hash SET NOT NULL;

ALTER TABLE sanctions_lists
    ALTER COLUMN source_ref TYPE VARCHAR(200);

CREATE UNIQUE INDEX ux_sanctions_provider_uid
    ON sanctions_lists(list_type, provider_uid);
CREATE INDEX idx_sanctions_normalized_name_active
    ON sanctions_lists(normalized_name, is_active);
CREATE INDEX idx_sanctions_aliases_normalized
    ON sanctions_lists USING GIN(aliases_normalized);

CREATE TABLE sanctions_import_runs (
    run_id              UUID PRIMARY KEY,
    provider_code       VARCHAR(10) NOT NULL,
    source_ref          VARCHAR(200),
    content_sha256      CHAR(64),
    status              VARCHAR(16) NOT NULL,
    fetched_at          TIMESTAMPTZ,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    parsed_count        INTEGER NOT NULL DEFAULT 0,
    inserted_count      INTEGER NOT NULL DEFAULT 0,
    updated_count       INTEGER NOT NULL DEFAULT 0,
    deactivated_count   INTEGER NOT NULL DEFAULT 0,
    error_code          VARCHAR(80),
    error_message       VARCHAR(1000),
    CONSTRAINT chk_sanctions_import_provider CHECK (provider_code IN ('BOL', 'OFAC', 'UN')),
    CONSTRAINT chk_sanctions_import_status CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED', 'REJECTED'))
);

CREATE INDEX idx_sanctions_import_runs_provider_completed
    ON sanctions_import_runs(provider_code, completed_at DESC);

CREATE TABLE sanctions_import_staging (
    batch_id             UUID NOT NULL,
    provider_code        VARCHAR(10) NOT NULL,
    provider_uid         VARCHAR(220) NOT NULL,
    entity_name          VARCHAR(500) NOT NULL,
    normalized_name      VARCHAR(500) NOT NULL,
    entity_type          VARCHAR(10) NOT NULL,
    aliases              JSONB NOT NULL DEFAULT '[]'::jsonb,
    aliases_normalized   JSONB NOT NULL DEFAULT '[]'::jsonb,
    identifiers          JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_ref           VARCHAR(200),
    content_hash         CHAR(64) NOT NULL,
    staged_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (batch_id, provider_code, provider_uid),
    CONSTRAINT chk_sanctions_staging_provider CHECK (provider_code IN ('BOL', 'OFAC', 'UN')),
    CONSTRAINT chk_sanctions_staging_entity_type CHECK (entity_type IN ('PERSON', 'ENTITY'))
);

CREATE INDEX idx_sanctions_import_staging_batch
    ON sanctions_import_staging(batch_id, provider_code);

-- Distinguish infrastructure failure from a genuine CLEAR result.
ALTER TABLE sanctions_screening_results
    DROP CONSTRAINT chk_screening_outcome;
ALTER TABLE sanctions_screening_results
    ADD CONSTRAINT chk_screening_outcome
    CHECK (outcome IN ('CLEAR', 'BLOCKED', 'MANUAL_REVIEW', 'ERROR'));
