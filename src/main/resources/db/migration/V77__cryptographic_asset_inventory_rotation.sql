CREATE TABLE IF NOT EXISTS cryptographic_asset_inventory (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(120) NOT NULL UNIQUE,
    asset_type VARCHAR(32) NOT NULL CHECK (asset_type IN ('KMS_KEY','VAULT_TRANSIT_KEY','TLS_CERTIFICATE','CA_CERTIFICATE','SIGNING_KEY','HMAC_KEY')),
    provider VARCHAR(64) NOT NULL,
    external_reference VARCHAR(500) NOT NULL,
    fingerprint_sha256 CHAR(64),
    algorithm VARCHAR(80) NOT NULL,
    key_size_bits INTEGER CHECK (key_size_bits IS NULL OR key_size_bits >= 128),
    owner_team VARCHAR(120) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED','ACTIVE','ROTATING','RETIRED','REVOKED','EXPIRED')),
    activated_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    rotation_interval_days INTEGER NOT NULL CHECK (rotation_interval_days BETWEEN 1 AND 3650),
    last_rotated_at TIMESTAMPTZ,
    next_rotation_at TIMESTAMPTZ NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (external_reference !~* '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|secret=|password=)')
);
CREATE INDEX IF NOT EXISTS idx_crypto_asset_rotation_due ON cryptographic_asset_inventory(status,next_rotation_at);
CREATE TABLE IF NOT EXISTS cryptographic_asset_binding (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES cryptographic_asset_inventory(id) ON DELETE RESTRICT,
    service_name VARCHAR(120) NOT NULL,
    usage_type VARCHAR(64) NOT NULL,
    configuration_reference VARCHAR(500) NOT NULL,
    criticality VARCHAR(16) NOT NULL CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(asset_id,service_name,usage_type)
);
CREATE TABLE IF NOT EXISTS cryptographic_rotation_plan (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES cryptographic_asset_inventory(id),
    planned_for TIMESTAMPTZ NOT NULL,
    overlap_until TIMESTAMPTZ,
    rollback_reference VARCHAR(500) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    executed_by VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','EXECUTING','COMPLETED','FAILED','ROLLED_BACK','CANCELLED')),
    old_fingerprint CHAR(64),
    new_fingerprint CHAR(64),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CHECK (executed_by IS NULL OR executed_by <> requested_by),
    CHECK (overlap_until IS NULL OR overlap_until >= planned_for)
);
CREATE TABLE IF NOT EXISTS cryptographic_rotation_evidence (
    id UUID PRIMARY KEY,
    rotation_plan_id UUID NOT NULL REFERENCES cryptographic_rotation_plan(id),
    evidence_type VARCHAR(64) NOT NULL,
    artifact_reference VARCHAR(1000) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    recorded_by VARCHAR(120) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
