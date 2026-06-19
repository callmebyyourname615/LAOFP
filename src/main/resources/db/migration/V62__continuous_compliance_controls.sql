CREATE TABLE IF NOT EXISTS compliance_control_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    control_code VARCHAR(100) NOT NULL UNIQUE,
    domain VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    frequency VARCHAR(40) NOT NULL,
    evidence_query TEXT NOT NULL,
    owner VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS compliance_control_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id VARCHAR(120) NOT NULL,
    control_code VARCHAR(100) NOT NULL REFERENCES compliance_control_definition(control_code),
    result VARCHAR(24) NOT NULL,
    evidence_uri TEXT,
    evidence_sha256 CHAR(64),
    exception_reference VARCHAR(120),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(run_id, control_code)
);
CREATE INDEX IF NOT EXISTS idx_compliance_control_run_result ON compliance_control_run(result, started_at DESC);
