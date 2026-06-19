CREATE TABLE IF NOT EXISTS release_change_window (
    id UUID PRIMARY KEY,
    window_name VARCHAR(160) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    approved_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_release_window_lookup ON release_change_window(environment,starts_at,ends_at);
CREATE TABLE IF NOT EXISTS release_freeze_period (
    id UUID PRIMARY KEY,
    environment VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('ADVISORY','HARD')),
    created_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (approved_by <> created_by),
    CHECK (ends_at > starts_at)
);
CREATE TABLE IF NOT EXISTS release_freeze_exception (
    id UUID PRIMARY KEY,
    freeze_period_id UUID NOT NULL REFERENCES release_freeze_period(id),
    release_reference VARCHAR(160) NOT NULL,
    justification VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','USED','EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(freeze_period_id,release_reference),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE TABLE IF NOT EXISTS release_gate_decision (
    id UUID PRIMARY KEY,
    release_reference VARCHAR(160) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW','DENY')),
    reason VARCHAR(1000) NOT NULL,
    evaluated_by VARCHAR(120) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
