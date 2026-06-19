CREATE TABLE IF NOT EXISTS participant_lifecycle_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_code VARCHAR(32) NOT NULL,
    case_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    effective_at TIMESTAMPTZ,
    sla_due_at TIMESTAMPTZ NOT NULL,
    checklist JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_assessment JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_uri TEXT,
    evidence_sha256 CHAR(64),
    UNIQUE(participant_code, case_type, status) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX IF NOT EXISTS idx_participant_lifecycle_status ON participant_lifecycle_case(status, sla_due_at);

CREATE TABLE IF NOT EXISTS participant_contact_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_code VARCHAR(32) NOT NULL,
    role VARCHAR(60) NOT NULL,
    contact_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(60),
    escalation_level INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    verified_at TIMESTAMPTZ,
    UNIQUE(participant_code, role, email)
);
