CREATE TABLE IF NOT EXISTS regulatory_report_definition (
    report_code VARCHAR(80) PRIMARY KEY,
    regulator_code VARCHAR(40) NOT NULL,
    frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','ANNUAL','AD_HOC')),
    schema_version VARCHAR(40) NOT NULL,
    due_time TIME,
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS regulatory_report_run (
    id UUID PRIMARY KEY,
    report_code VARCHAR(80) NOT NULL REFERENCES regulatory_report_definition(report_code),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATING','VALIDATED','FAILED','SUBMITTED','ACKNOWLEDGED','REJECTED')),
    generated_by VARCHAR(120) NOT NULL,
    validated_by VARCHAR(120),
    record_count BIGINT,
    total_amount NUMERIC(28,4),
    evidence_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(report_code,period_start,period_end),
    CHECK (period_end >= period_start),
    CHECK (validated_by IS NULL OR validated_by <> generated_by)
);
CREATE TABLE IF NOT EXISTS regulatory_report_artifact (
    id UUID PRIMARY KEY,
    report_run_id UUID NOT NULL REFERENCES regulatory_report_run(id) ON DELETE RESTRICT,
    object_key VARCHAR(1000) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    encrypted BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(report_run_id,object_key)
);
CREATE TABLE IF NOT EXISTS regulatory_report_submission (
    id UUID PRIMARY KEY,
    report_run_id UUID NOT NULL REFERENCES regulatory_report_run(id),
    submission_reference VARCHAR(200) NOT NULL UNIQUE,
    submitted_by VARCHAR(120) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledgement_code VARCHAR(120),
    acknowledgement_hash CHAR(64),
    acknowledged_at TIMESTAMPTZ,
    response_status VARCHAR(20) CHECK (response_status IN ('ACCEPTED','REJECTED','PENDING'))
);
CREATE INDEX IF NOT EXISTS idx_regulatory_report_due ON regulatory_report_run(status,period_end);
