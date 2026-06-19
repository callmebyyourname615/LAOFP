CREATE TABLE legal_holds (
    id BIGSERIAL PRIMARY KEY,
    hold_ref VARCHAR(64) NOT NULL UNIQUE,
    scope_type VARCHAR(24) NOT NULL,
    scope_key VARCHAR(160) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    reason VARCHAR(1000) NOT NULL,
    case_reference VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(160) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    approved_by VARCHAR(160),
    approved_at TIMESTAMP,
    release_requested_by VARCHAR(160),
    release_requested_at TIMESTAMP,
    released_by VARCHAR(160),
    released_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_legal_hold_scope CHECK (scope_type IN ('TABLE','REFERENCE')),
    CONSTRAINT ck_legal_hold_status CHECK (status IN ('PENDING','ACTIVE','RELEASE_REQUESTED','RELEASED','REJECTED')),
    CONSTRAINT ck_legal_hold_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_legal_hold_approval_separation CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CONSTRAINT ck_legal_hold_release_separation CHECK (released_by IS NULL OR released_by <> release_requested_by)
);
CREATE INDEX idx_legal_holds_active_scope ON legal_holds(scope_type, scope_key, status);
CREATE INDEX idx_legal_holds_dates ON legal_holds(effective_from, effective_to);

CREATE TABLE retention_execution_log (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(160) NOT NULL,
    target_table VARCHAR(160) NOT NULL,
    business_date DATE,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    affected_rows BIGINT,
    legal_hold_ref VARCHAR(64),
    details VARCHAR(1000),
    executed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_retention_action CHECK (action IN ('DROP_PARTITION')),
    CONSTRAINT ck_retention_status CHECK (status IN ('BLOCKED','SUCCEEDED','FAILED'))
);
CREATE INDEX idx_retention_execution_date ON retention_execution_log(executed_at DESC);
