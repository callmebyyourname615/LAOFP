CREATE TABLE IF NOT EXISTS tariff_plan (
    id UUID PRIMARY KEY,
    plan_code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL,
    participant_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS tariff_version (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES tariff_plan(id),
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED','REJECTED')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    approval_reason VARCHAR(500),
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(plan_id,version_no),
    CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tariff_active_version ON tariff_version(plan_id) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS tariff_rule (
    id UUID PRIMARY KEY,
    tariff_version_id UUID NOT NULL REFERENCES tariff_version(id) ON DELETE CASCADE,
    message_type VARCHAR(80) NOT NULL,
    currency CHAR(3) NOT NULL,
    minimum_amount NUMERIC(24,4) NOT NULL DEFAULT 0,
    maximum_amount NUMERIC(24,4),
    flat_fee NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (flat_fee >= 0),
    rate_basis_points NUMERIC(12,4) NOT NULL DEFAULT 0 CHECK (rate_basis_points >= 0),
    minimum_fee NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (minimum_fee >= 0),
    maximum_fee NUMERIC(24,4),
    priority INTEGER NOT NULL DEFAULT 100,
    CHECK (maximum_amount IS NULL OR maximum_amount >= minimum_amount),
    CHECK (maximum_fee IS NULL OR maximum_fee >= minimum_fee)
);
CREATE INDEX IF NOT EXISTS idx_tariff_rule_lookup ON tariff_rule(tariff_version_id,message_type,currency,priority);
CREATE TABLE IF NOT EXISTS fee_assessment (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(160) NOT NULL UNIQUE,
    tariff_version_id UUID NOT NULL REFERENCES tariff_version(id),
    tariff_rule_id UUID NOT NULL REFERENCES tariff_rule(id),
    amount NUMERIC(24,4) NOT NULL,
    assessed_fee NUMERIC(24,4) NOT NULL CHECK (assessed_fee >= 0),
    currency CHAR(3) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
