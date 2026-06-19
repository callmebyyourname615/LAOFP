CREATE TABLE IF NOT EXISTS fx_governance_policy (
    currency_pair VARCHAR(7) PRIMARY KEY,
    minimum_quorum INTEGER NOT NULL CHECK (minimum_quorum >= 2),
    maximum_age_seconds INTEGER NOT NULL CHECK (maximum_age_seconds BETWEEN 1 AND 86400),
    maximum_deviation_basis_points NUMERIC(12,4) NOT NULL CHECK (maximum_deviation_basis_points > 0),
    quote_ttl_seconds INTEGER NOT NULL CHECK (quote_ttl_seconds BETWEEN 1 AND 86400),
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS fx_rate_provider (
    provider_code VARCHAR(80) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT false,
    trust_weight INTEGER NOT NULL DEFAULT 100 CHECK (trust_weight BETWEEN 1 AND 1000),
    last_success_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS fx_rate_observation (
    id UUID PRIMARY KEY,
    provider_code VARCHAR(80) NOT NULL REFERENCES fx_rate_provider(provider_code),
    currency_pair VARCHAR(7) NOT NULL,
    rate NUMERIC(24,10) NOT NULL CHECK (rate > 0),
    observed_at TIMESTAMPTZ NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider_code,currency_pair,observed_at)
);
CREATE INDEX IF NOT EXISTS idx_fx_observation_fresh ON fx_rate_observation(currency_pair,observed_at DESC);
CREATE TABLE IF NOT EXISTS governed_fx_rate_publication (
    id UUID PRIMARY KEY,
    currency_pair VARCHAR(7) NOT NULL,
    rate NUMERIC(24,10) NOT NULL CHECK (rate > 0),
    provider_count INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','REJECTED','EXPIRED')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CHECK (valid_until > valid_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fx_approved_pair ON governed_fx_rate_publication(currency_pair) WHERE status='APPROVED';
