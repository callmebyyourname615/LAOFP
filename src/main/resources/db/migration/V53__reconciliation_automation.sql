CREATE TABLE IF NOT EXISTS reconciliation_control_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_date DATE NOT NULL,
    source_system VARCHAR(80) NOT NULL,
    target_system VARCHAR(80) NOT NULL,
    run_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expected_count BIGINT NOT NULL DEFAULT 0,
    actual_count BIGINT NOT NULL DEFAULT 0,
    expected_amount NUMERIC(20,2) NOT NULL DEFAULT 0,
    actual_amount NUMERIC(20,2) NOT NULL DEFAULT 0,
    mismatch_count BIGINT NOT NULL DEFAULT 0,
    evidence_uri TEXT,
    evidence_sha256 CHAR(64),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_by VARCHAR(120) NOT NULL DEFAULT 'system',
    UNIQUE (business_date, source_system, target_system, run_type)
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_control_run_status ON reconciliation_control_run(status, business_date DESC);

CREATE TABLE IF NOT EXISTS reconciliation_exception_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES reconciliation_control_run(id),
    transaction_reference VARCHAR(120) NOT NULL,
    participant_code VARCHAR(32) NOT NULL,
    exception_type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    expected_payload_hash CHAR(64),
    actual_payload_hash CHAR(64),
    assigned_to VARCHAR(120),
    sla_due_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(run_id, transaction_reference, exception_type)
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_exception_status ON reconciliation_exception_case(status, sla_due_at);
