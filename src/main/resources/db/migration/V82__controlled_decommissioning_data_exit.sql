CREATE TABLE IF NOT EXISTS decommission_plan (
    id UUID PRIMARY KEY,
    plan_reference VARCHAR(160) NOT NULL UNIQUE,
    target_type VARCHAR(24) NOT NULL CHECK (target_type IN ('PARTICIPANT','CONNECTOR','PRODUCT','SERVICE','DATASET')),
    target_code VARCHAR(160) NOT NULL,
    planned_effective_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    data_exit_required BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','BLOCKED','EXECUTING','COMPLETED','CANCELLED','ROLLED_BACK')),
    requested_by VARCHAR(120) NOT NULL,
    operations_approved_by VARCHAR(120),
    risk_approved_by VARCHAR(120),
    business_approved_by VARCHAR(120),
    rollback_reference VARCHAR(1000) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CHECK (operations_approved_by IS NULL OR operations_approved_by <> requested_by),
    CHECK (risk_approved_by IS NULL OR risk_approved_by NOT IN (requested_by,operations_approved_by)),
    CHECK (business_approved_by IS NULL OR business_approved_by NOT IN (requested_by,operations_approved_by,risk_approved_by))
);
CREATE TABLE IF NOT EXISTS decommission_task (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES decommission_plan(id) ON DELETE RESTRICT,
    task_code VARCHAR(80) NOT NULL,
    task_order INTEGER NOT NULL CHECK (task_order > 0),
    owner_team VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    blocking BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','READY','RUNNING','DONE','FAILED','WAIVED')),
    completion_evidence_hash CHAR(64),
    completed_by VARCHAR(120),
    completed_at TIMESTAMPTZ,
    UNIQUE(plan_id,task_code),
    UNIQUE(plan_id,task_order)
);
CREATE TABLE IF NOT EXISTS decommission_data_exit_artifact (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES decommission_plan(id),
    artifact_type VARCHAR(64) NOT NULL,
    artifact_reference VARCHAR(1500) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    encrypted BOOLEAN NOT NULL,
    recipient_reference VARCHAR(500),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    retention_until DATE,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (NOT encrypted OR recipient_reference IS NOT NULL)
);
CREATE TABLE IF NOT EXISTS decommission_execution_event (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES decommission_plan(id),
    event_type VARCHAR(64) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    detail VARCHAR(2000) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_decommission_plan_effective ON decommission_plan(status,planned_effective_at);
