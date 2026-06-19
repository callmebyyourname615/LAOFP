CREATE TABLE IF NOT EXISTS settlement_calendar (
    id UUID PRIMARY KEY,
    calendar_code VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    timezone VARCHAR(80) NOT NULL,
    weekend_days SMALLINT[] NOT NULL DEFAULT ARRAY[6,7]::SMALLINT[],
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED')),
    effective_from DATE NOT NULL,
    effective_until DATE,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(calendar_code,version),
    CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_calendar_active ON settlement_calendar(calendar_code) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS settlement_calendar_holiday (
    id UUID PRIMARY KEY,
    calendar_id UUID NOT NULL REFERENCES settlement_calendar(id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(200) NOT NULL,
    full_day BOOLEAN NOT NULL DEFAULT true,
    early_close_time TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(calendar_id,holiday_date),
    CHECK (full_day OR early_close_time IS NOT NULL)
);
CREATE TABLE IF NOT EXISTS settlement_cutoff_rule (
    id UUID PRIMARY KEY,
    calendar_id UUID NOT NULL REFERENCES settlement_calendar(id) ON DELETE CASCADE,
    cycle_code VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    submission_cutoff TIME NOT NULL,
    finality_cutoff TIME NOT NULL,
    late_action VARCHAR(20) NOT NULL CHECK (late_action IN ('REJECT','NEXT_CYCLE','MANUAL_REVIEW')),
    grace_seconds INTEGER NOT NULL DEFAULT 0 CHECK (grace_seconds BETWEEN 0 AND 3600),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(calendar_id,cycle_code,product_code),
    CHECK (finality_cutoff >= submission_cutoff)
);
CREATE TABLE IF NOT EXISTS settlement_calendar_change_request (
    id UUID PRIMARY KEY,
    calendar_code VARCHAR(64) NOT NULL,
    proposed_version INTEGER NOT NULL,
    bundle_hash CHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','APPLIED','STALE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ,
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE TABLE IF NOT EXISTS settlement_cutoff_decision (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(160) NOT NULL,
    calendar_id UUID NOT NULL REFERENCES settlement_calendar(id),
    cutoff_rule_id UUID NOT NULL REFERENCES settlement_cutoff_rule(id),
    submitted_at TIMESTAMPTZ NOT NULL,
    business_date DATE NOT NULL,
    decision VARCHAR(24) NOT NULL CHECK (decision IN ('ACCEPT','REJECT','NEXT_CYCLE','MANUAL_REVIEW')),
    reason VARCHAR(500) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_settlement_holiday_date ON settlement_calendar_holiday(holiday_date);
CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_cutoff_decision_reference
    ON settlement_cutoff_decision(transaction_reference);
