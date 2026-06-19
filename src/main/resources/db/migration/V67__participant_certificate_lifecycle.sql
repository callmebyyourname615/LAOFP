CREATE TABLE IF NOT EXISTS participant_certificate (
    id UUID PRIMARY KEY,
    participant_code VARCHAR(32) NOT NULL,
    certificate_type VARCHAR(24) NOT NULL CHECK (certificate_type IN ('CLIENT_MTLS','MESSAGE_SIGNING','WEBHOOK_SIGNING')),
    fingerprint_sha256 CHAR(64) NOT NULL UNIQUE,
    serial_number VARCHAR(160) NOT NULL,
    subject_dn VARCHAR(1000) NOT NULL,
    issuer_dn VARCHAR(1000) NOT NULL,
    not_before TIMESTAMPTZ NOT NULL,
    not_after TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','OVERLAP','REVOKED','EXPIRED','REJECTED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    replaced_certificate_id UUID REFERENCES participant_certificate(id),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    CHECK (not_after > not_before),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE INDEX IF NOT EXISTS idx_participant_certificate_expiry ON participant_certificate(status,not_after);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_participant_cert ON participant_certificate(participant_code,certificate_type) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS certificate_lifecycle_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_id UUID NOT NULL REFERENCES participant_certificate(id),
    event_type VARCHAR(40) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    reason VARCHAR(500),
    evidence_hash CHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
