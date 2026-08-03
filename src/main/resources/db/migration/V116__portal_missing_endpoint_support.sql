ALTER TABLE fraud_velocity_rule
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_audit_logs_security_events
    ON audit_logs(event_type, created_at DESC)
    WHERE event_type LIKE '%LOGIN%'
       OR event_type LIKE '%MFA%'
       OR event_type LIKE '%SESSION%'
       OR event_type LIKE '%BREAK_GLASS%'
       OR event_type LIKE '%KEY%';

CREATE TABLE IF NOT EXISTS fee_exception (
    id UUID PRIMARY KEY,
    tariff_rule_id UUID NOT NULL REFERENCES tariff_rule(id),
    participant_code VARCHAR(32),
    message_type VARCHAR(80),
    override_type VARCHAR(20) NOT NULL,
    override_value NUMERIC(24,4),
    reason TEXT NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by VARCHAR(120),
    approved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    CONSTRAINT chk_fee_exception_status CHECK (status IN ('PENDING','APPROVED','REJECTED','ACTIVE','EXPIRED')),
    CONSTRAINT chk_fee_exception_override_type CHECK (override_type IN ('WAIVER','FIXED','PERCENTAGE')),
    CONSTRAINT chk_fee_exception_dates CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);

CREATE INDEX IF NOT EXISTS idx_fee_exception_status_requested
    ON fee_exception(status, requested_at DESC);
