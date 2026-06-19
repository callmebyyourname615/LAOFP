CREATE TABLE IF NOT EXISTS synthetic_probe_definition (
    probe_code VARCHAR(80) PRIMARY KEY,
    participant_code VARCHAR(32) NOT NULL,
    probe_type VARCHAR(40) NOT NULL CHECK (probe_type IN ('INQUIRY','VPA_LOOKUP','TRANSFER','WEBHOOK','END_TO_END')),
    schedule_cron VARCHAR(100) NOT NULL,
    timeout_seconds INTEGER NOT NULL CHECK (timeout_seconds BETWEEN 1 AND 300),
    maximum_amount NUMERIC(20,4) NOT NULL DEFAULT 0 CHECK (maximum_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'LAK',
    enabled BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (participant_code LIKE 'SYN%')
);
CREATE TABLE IF NOT EXISTS synthetic_probe_execution (
    id UUID PRIMARY KEY,
    probe_code VARCHAR(80) NOT NULL REFERENCES synthetic_probe_definition(probe_code),
    synthetic_reference VARCHAR(160) NOT NULL UNIQUE,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','PASS','FAIL','TIMEOUT','CLEANUP_FAILED')),
    latency_ms BIGINT,
    response_code VARCHAR(80),
    cleanup_status VARCHAR(20),
    evidence_hash CHAR(64),
    error_summary VARCHAR(500),
    CHECK (synthetic_reference LIKE 'SYN-%')
);
CREATE INDEX IF NOT EXISTS idx_synthetic_probe_latest ON synthetic_probe_execution(probe_code,started_at DESC);
