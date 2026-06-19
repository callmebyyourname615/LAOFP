CREATE TABLE IF NOT EXISTS participant_liquidity_control (
    participant_code VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    available_balance NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    reserved_balance NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (reserved_balance >= 0),
    minimum_operating_balance NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (minimum_operating_balance >= 0),
    warning_threshold NUMERIC(24,4) NOT NULL DEFAULT 0 CHECK (warning_threshold >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(participant_code,currency),
    CHECK (reserved_balance <= available_balance)
);

CREATE TABLE IF NOT EXISTS liquidity_fund_reservation (
    id UUID PRIMARY KEY,
    reservation_reference VARCHAR(160) NOT NULL UNIQUE,
    participant_code VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    amount NUMERIC(24,4) NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED','SETTLED','RELEASED','EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    FOREIGN KEY(participant_code,currency) REFERENCES participant_liquidity_control(participant_code,currency)
);
CREATE INDEX IF NOT EXISTS idx_liquidity_reservation_expiry ON liquidity_fund_reservation(status, expires_at);

CREATE TABLE IF NOT EXISTS liquidity_control_breach (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_code VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    breach_type VARCHAR(40) NOT NULL,
    headroom NUMERIC(24,4) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_liquidity_breach_open ON liquidity_control_breach(detected_at DESC) WHERE resolved_at IS NULL;
