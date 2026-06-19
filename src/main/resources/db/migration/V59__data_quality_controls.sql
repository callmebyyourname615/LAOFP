CREATE TABLE IF NOT EXISTS data_quality_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code VARCHAR(100) NOT NULL UNIQUE,
    table_name VARCHAR(120) NOT NULL,
    rule_type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    sql_check TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    owner VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS data_quality_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id VARCHAR(120) NOT NULL,
    rule_code VARCHAR(100) NOT NULL REFERENCES data_quality_rule(rule_code),
    status VARCHAR(24) NOT NULL,
    failing_count BIGINT NOT NULL DEFAULT 0,
    sample_rows JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(run_id, rule_code)
);
CREATE INDEX IF NOT EXISTS idx_data_quality_run_status ON data_quality_run(status, started_at DESC);
