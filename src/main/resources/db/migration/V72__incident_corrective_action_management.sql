CREATE TABLE IF NOT EXISTS incident_record (
    id UUID PRIMARY KEY,
    incident_reference VARCHAR(80) NOT NULL UNIQUE,
    severity VARCHAR(8) NOT NULL CHECK (severity IN ('SEV1','SEV2','SEV3','SEV4')),
    title VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','MITIGATED','RCA_PENDING','ACTION_TRACKING','CLOSED')),
    incident_commander VARCHAR(120) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    mitigated_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    root_cause TEXT,
    customer_impact TEXT,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS incident_timeline_event (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident_record(id) ON DELETE RESTRICT,
    event_time TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    details TEXT NOT NULL,
    actor VARCHAR(120) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS corrective_action (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident_record(id),
    action_type VARCHAR(20) NOT NULL CHECK (action_type IN ('CORRECTIVE','PREVENTIVE','DETECTION','PROCESS')),
    priority VARCHAR(12) NOT NULL CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    description TEXT NOT NULL,
    owner VARCHAR(120) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','BLOCKED','DONE','RISK_ACCEPTED')),
    completed_at TIMESTAMPTZ,
    completion_evidence_hash CHAR(64),
    risk_accepted_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_corrective_action_due ON corrective_action(status,due_at);
CREATE TABLE IF NOT EXISTS incident_closure_approval (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident_record(id),
    approval_role VARCHAR(40) NOT NULL CHECK (approval_role IN ('ENGINEERING','OPERATIONS','SECURITY','BUSINESS')),
    approver VARCHAR(120) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    comment VARCHAR(1000),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(incident_id,approval_role),
    UNIQUE(incident_id,approver)
);
