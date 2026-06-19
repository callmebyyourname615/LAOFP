CREATE TABLE IF NOT EXISTS ops_daily_control_room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_date DATE NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    opening_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    closing_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    opened_by VARCHAR(120) NOT NULL,
    closed_by VARCHAR(120),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    close_evidence_sha256 CHAR(64)
);

CREATE TABLE IF NOT EXISTS ops_control_room_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    control_room_id UUID NOT NULL REFERENCES ops_daily_control_room(id),
    task_code VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    owner VARCHAR(120) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMPTZ,
    evidence_uri TEXT,
    evidence_sha256 CHAR(64),
    UNIQUE(control_room_id, task_code)
);
