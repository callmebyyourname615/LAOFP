CREATE TABLE database_maintenance_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE,
    operation VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    details_json TEXT,
    CONSTRAINT ck_database_maintenance_status CHECK (status IN ('STARTED','SUCCEEDED','FAILED','BLOCKED'))
);
CREATE INDEX idx_database_maintenance_started ON database_maintenance_runs(started_at DESC);
