CREATE TABLE IF NOT EXISTS settlement_evidence_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_cycle_id VARCHAR(120) NOT NULL,
    evidence_type VARCHAR(60) NOT NULL,
    participant_code VARCHAR(32),
    amount NUMERIC(20,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'LAK',
    source_uri TEXT NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    previous_hash CHAR(64),
    chain_hash CHAR(64) NOT NULL,
    created_by VARCHAR(120) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(settlement_cycle_id, evidence_type, participant_code, source_sha256)
);
CREATE INDEX IF NOT EXISTS idx_settlement_evidence_cycle ON settlement_evidence_ledger(settlement_cycle_id, created_at);

CREATE TABLE IF NOT EXISTS settlement_dispute_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_case_id VARCHAR(120) NOT NULL,
    evidence_ledger_id UUID NOT NULL REFERENCES settlement_evidence_ledger(id),
    submitted_by VARCHAR(120) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted BOOLEAN NOT NULL DEFAULT false,
    accepted_by VARCHAR(120),
    accepted_at TIMESTAMPTZ
);
