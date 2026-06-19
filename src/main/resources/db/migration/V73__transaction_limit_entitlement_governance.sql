CREATE TABLE IF NOT EXISTS participant_product_entitlement (
    id UUID PRIMARY KEY,
    participant_code VARCHAR(32) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_until IS NULL OR effective_until > effective_from),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_participant_product_entitlement_active
    ON participant_product_entitlement(participant_code,product_code,channel,currency)
    WHERE status='ACTIVE';

CREATE TABLE IF NOT EXISTS transaction_limit_policy (
    id UUID PRIMARY KEY,
    policy_name VARCHAR(160) NOT NULL,
    scope_type VARCHAR(24) NOT NULL CHECK (scope_type IN ('SYSTEM','PARTICIPANT','PRODUCT','PARTICIPANT_PRODUCT')),
    scope_value VARCHAR(160) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    per_transaction_amount NUMERIC(24,4) NOT NULL CHECK (per_transaction_amount > 0),
    hourly_amount NUMERIC(24,4) CHECK (hourly_amount IS NULL OR hourly_amount > 0),
    daily_amount NUMERIC(24,4) CHECK (daily_amount IS NULL OR daily_amount > 0),
    daily_count BIGINT CHECK (daily_count IS NULL OR daily_count > 0),
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Vientiane',
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED','REJECTED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(policy_name,version),
    CHECK (effective_until IS NULL OR effective_until > effective_from),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_limit_policy_active
    ON transaction_limit_policy(scope_type,scope_value,product_code,channel,currency)
    WHERE status='ACTIVE';

CREATE TABLE IF NOT EXISTS transaction_limit_consumption (
    policy_id UUID NOT NULL REFERENCES transaction_limit_policy(id) ON DELETE RESTRICT,
    participant_code VARCHAR(32) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    window_type VARCHAR(12) NOT NULL CHECK (window_type IN ('HOUR','DAY')),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    consumed_amount NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (consumed_amount >= 0),
    consumed_count BIGINT NOT NULL DEFAULT 0 CHECK (consumed_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(policy_id,participant_code,window_type,window_start),
    CHECK (window_end > window_start)
);

CREATE TABLE IF NOT EXISTS transaction_limit_override_request (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES transaction_limit_policy(id),
    participant_code VARCHAR(32) NOT NULL,
    transaction_reference VARCHAR(160) NOT NULL,
    requested_amount NUMERIC(24,4) NOT NULL CHECK (requested_amount > 0),
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','USED','EXPIRED')),
    used_at TIMESTAMPTZ,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(policy_id,transaction_reference),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

CREATE TABLE IF NOT EXISTS transaction_limit_decision_audit (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(160) NOT NULL,
    participant_code VARCHAR(32) NOT NULL,
    policy_id UUID REFERENCES transaction_limit_policy(id),
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW','DENY','OVERRIDE')),
    amount NUMERIC(24,4) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_limit_decision_audit_reference ON transaction_limit_decision_audit(transaction_reference);
CREATE INDEX IF NOT EXISTS idx_limit_consumption_window ON transaction_limit_consumption(window_end);
CREATE UNIQUE INDEX IF NOT EXISTS uq_limit_final_decision_reference
    ON transaction_limit_decision_audit(transaction_reference)
    WHERE decision IN ('ALLOW','DENY');
