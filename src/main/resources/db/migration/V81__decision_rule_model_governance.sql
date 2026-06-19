CREATE TABLE IF NOT EXISTS decision_rule_package (
    id UUID PRIMARY KEY,
    package_code VARCHAR(120) NOT NULL UNIQUE,
    domain VARCHAR(24) NOT NULL CHECK (domain IN ('FRAUD','AML','SANCTIONS','ROUTING','LIMITS','RISK_SCORING')),
    owner_team VARCHAR(120) NOT NULL,
    criticality VARCHAR(16) NOT NULL CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DEPRECATED','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS decision_rule_version (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES decision_rule_package(id) ON DELETE RESTRICT,
    version VARCHAR(80) NOT NULL,
    artifact_reference VARCHAR(1000) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    risk_approved_by VARCHAR(120),
    compliance_approved_by VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','TESTING','READY','APPROVED','ACTIVE','REJECTED','RETIRED','ROLLED_BACK')),
    effective_from TIMESTAMPTZ,
    effective_until TIMESTAMPTZ,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(package_id,version),
    CHECK (risk_approved_by IS NULL OR risk_approved_by <> requested_by),
    CHECK (compliance_approved_by IS NULL OR (compliance_approved_by <> requested_by AND compliance_approved_by <> risk_approved_by)),
    CHECK (effective_until IS NULL OR effective_until > effective_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_decision_rule_active_version ON decision_rule_version(package_id) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS decision_rule_test_execution (
    id UUID PRIMARY KEY,
    rule_version_id UUID NOT NULL REFERENCES decision_rule_version(id),
    suite_version VARCHAR(80) NOT NULL,
    test_case_count INTEGER NOT NULL CHECK (test_case_count > 0),
    passed_count INTEGER NOT NULL CHECK (passed_count >= 0),
    failed_count INTEGER NOT NULL CHECK (failed_count >= 0),
    false_positive_rate NUMERIC(7,5) CHECK (false_positive_rate IS NULL OR false_positive_rate BETWEEN 0 AND 1),
    false_negative_rate NUMERIC(7,5) CHECK (false_negative_rate IS NULL OR false_negative_rate BETWEEN 0 AND 1),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PASS','FAIL')),
    result_artifact_reference VARCHAR(1000) NOT NULL,
    result_sha256 CHAR(64) NOT NULL,
    executed_by VARCHAR(120) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (passed_count + failed_count = test_case_count)
);
CREATE TABLE IF NOT EXISTS decision_rule_deployment (
    id UUID PRIMARY KEY,
    rule_version_id UUID NOT NULL REFERENCES decision_rule_version(id),
    environment VARCHAR(32) NOT NULL,
    deployment_reference VARCHAR(160) NOT NULL,
    previous_version_id UUID REFERENCES decision_rule_version(id),
    deployed_by VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED','CANARY','ACTIVE','FAILED','ROLLED_BACK')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    evidence_hash CHAR(64) NOT NULL,
    UNIQUE(environment,deployment_reference)
);
