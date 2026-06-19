CREATE TABLE IF NOT EXISTS region_readiness_probe (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_code VARCHAR(40) NOT NULL,
    probe_type VARCHAR(60) NOT NULL,
    status VARCHAR(24) NOT NULL,
    latency_ms INTEGER,
    replication_lag_seconds INTEGER,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_region_readiness_probe_latest ON region_readiness_probe(region_code, probe_type, observed_at DESC);

CREATE TABLE IF NOT EXISTS region_failover_candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_code VARCHAR(40) NOT NULL,
    candidate_status VARCHAR(32) NOT NULL DEFAULT 'NOT_READY',
    last_probe_at TIMESTAMPTZ,
    blocker_summary JSONB NOT NULL DEFAULT '[]'::jsonb,
    approved_by VARCHAR(120),
    approved_at TIMESTAMPTZ,
    UNIQUE(region_code)
);
