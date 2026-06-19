CREATE TABLE IF NOT EXISTS third_party_dependency (
    id UUID PRIMARY KEY,
    dependency_code VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    dependency_type VARCHAR(32) NOT NULL CHECK (dependency_type IN ('RTGS','FIU','SANCTIONS','OBJECT_STORAGE','KMS','VAULT','NOTIFICATION','IDENTITY','NETWORK','OTHER')),
    endpoint_reference VARCHAR(500) NOT NULL,
    owner_team VARCHAR(120) NOT NULL,
    criticality VARCHAR(16) NOT NULL CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS third_party_sla_policy (
    id UUID PRIMARY KEY,
    dependency_id UUID NOT NULL REFERENCES third_party_dependency(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    availability_target NUMERIC(7,5) NOT NULL CHECK (availability_target > 0 AND availability_target <= 1),
    latency_p95_ms INTEGER NOT NULL CHECK (latency_p95_ms > 0),
    failure_threshold INTEGER NOT NULL CHECK (failure_threshold > 0),
    recovery_success_threshold INTEGER NOT NULL CHECK (recovery_success_threshold > 0),
    open_seconds INTEGER NOT NULL CHECK (open_seconds BETWEEN 1 AND 86400),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(dependency_id,version),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_third_party_active_sla ON third_party_sla_policy(dependency_id) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS third_party_health_sample (
    id UUID PRIMARY KEY,
    dependency_id UUID NOT NULL REFERENCES third_party_dependency(id),
    observed_at TIMESTAMPTZ NOT NULL,
    success BOOLEAN NOT NULL,
    latency_ms INTEGER CHECK (latency_ms IS NULL OR latency_ms >= 0),
    response_class VARCHAR(32),
    evidence_hash CHAR(64) NOT NULL,
    UNIQUE(dependency_id,observed_at)
);
CREATE INDEX IF NOT EXISTS idx_third_party_sample_recent ON third_party_health_sample(dependency_id,observed_at DESC);
CREATE TABLE IF NOT EXISTS third_party_circuit_state (
    dependency_id UUID PRIMARY KEY REFERENCES third_party_dependency(id),
    state VARCHAR(16) NOT NULL CHECK (state IN ('CLOSED','OPEN','HALF_OPEN','FORCED_OPEN','FORCED_CLOSED')),
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    consecutive_successes INTEGER NOT NULL DEFAULT 0,
    opened_at TIMESTAMPTZ,
    next_probe_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS third_party_override_request (
    id UUID PRIMARY KEY,
    dependency_id UUID NOT NULL REFERENCES third_party_dependency(id),
    requested_state VARCHAR(16) NOT NULL CHECK (requested_state IN ('FORCED_OPEN','FORCED_CLOSED')),
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXPIRED','REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
