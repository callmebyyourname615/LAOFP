CREATE TABLE IF NOT EXISTS fraud_velocity_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code VARCHAR(80) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    window_seconds INTEGER NOT NULL CHECK (window_seconds BETWEEN 1 AND 86400),
    max_count INTEGER,
    max_amount NUMERIC(20,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'LAK',
    action VARCHAR(24) NOT NULL CHECK (action IN ('ALLOW','REVIEW','REJECT','HOLD')),
    enabled BOOLEAN NOT NULL DEFAULT false,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fraud_velocity_decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(120) NOT NULL,
    participant_code VARCHAR(32) NOT NULL,
    subject_key VARCHAR(200) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    matched_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_score INTEGER NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(transaction_reference)
);
CREATE INDEX IF NOT EXISTS idx_fraud_velocity_decision_subject ON fraud_velocity_decision(subject_key, created_at DESC);
