CREATE TABLE IF NOT EXISTS privacy_access_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_reference VARCHAR(120) NOT NULL UNIQUE,
    requester_type VARCHAR(40) NOT NULL,
    subject_reference VARCHAR(200) NOT NULL,
    case_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    legal_basis VARCHAR(120) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    response_uri TEXT,
    response_sha256 CHAR(64),
    redaction_summary JSONB NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_privacy_access_case_status ON privacy_access_case(status, due_at);

CREATE TABLE IF NOT EXISTS pii_discovery_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id VARCHAR(120) NOT NULL,
    table_name VARCHAR(120) NOT NULL,
    column_name VARCHAR(120) NOT NULL,
    pii_category VARCHAR(80) NOT NULL,
    confidence INTEGER NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    sample_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(scan_id, table_name, column_name)
);
