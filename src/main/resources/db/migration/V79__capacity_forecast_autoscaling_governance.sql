CREATE TABLE IF NOT EXISTS capacity_observation (
    id UUID PRIMARY KEY,
    component VARCHAR(80) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    request_rate NUMERIC(24,4) NOT NULL CHECK (request_rate >= 0),
    cpu_utilization NUMERIC(7,4) CHECK (cpu_utilization BETWEEN 0 AND 1),
    memory_utilization NUMERIC(7,4) CHECK (memory_utilization BETWEEN 0 AND 1),
    p95_latency_ms NUMERIC(16,4) CHECK (p95_latency_ms IS NULL OR p95_latency_ms >= 0),
    error_rate NUMERIC(7,5) CHECK (error_rate IS NULL OR (error_rate BETWEEN 0 AND 1)),
    active_replicas INTEGER NOT NULL CHECK (active_replicas > 0),
    evidence_hash CHAR(64) NOT NULL,
    UNIQUE(component,environment,observed_at)
);
CREATE TABLE IF NOT EXISTS capacity_forecast (
    id UUID PRIMARY KEY,
    component VARCHAR(80) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    horizon_days INTEGER NOT NULL CHECK (horizon_days BETWEEN 1 AND 365),
    forecast_for TIMESTAMPTZ NOT NULL,
    forecast_request_rate NUMERIC(24,4) NOT NULL CHECK (forecast_request_rate >= 0),
    required_replicas INTEGER NOT NULL CHECK (required_replicas > 0),
    confidence_lower NUMERIC(24,4) NOT NULL,
    confidence_upper NUMERIC(24,4) NOT NULL,
    model_version VARCHAR(80) NOT NULL,
    source_window_start TIMESTAMPTZ NOT NULL,
    source_window_end TIMESTAMPTZ NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (confidence_upper >= confidence_lower),
    CHECK (source_window_end > source_window_start)
);
CREATE TABLE IF NOT EXISTS governed_autoscaling_policy (
    id UUID PRIMARY KEY,
    component VARCHAR(80) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    min_replicas INTEGER NOT NULL CHECK (min_replicas > 0),
    max_replicas INTEGER NOT NULL CHECK (max_replicas >= min_replicas),
    target_cpu_percent INTEGER CHECK (target_cpu_percent BETWEEN 1 AND 95),
    target_memory_percent INTEGER CHECK (target_memory_percent BETWEEN 1 AND 95),
    scale_up_percent INTEGER NOT NULL CHECK (scale_up_percent BETWEEN 1 AND 400),
    scale_down_percent INTEGER NOT NULL CHECK (scale_down_percent BETWEEN 1 AND 100),
    stabilization_seconds INTEGER NOT NULL CHECK (stabilization_seconds BETWEEN 0 AND 3600),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED','REJECTED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(component,environment,version),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_governed_autoscaling_active ON governed_autoscaling_policy(component,environment) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS capacity_change_request (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES governed_autoscaling_policy(id),
    forecast_id UUID REFERENCES capacity_forecast(id),
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    operations_approved_by VARCHAR(120),
    performance_approved_by VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','PARTIALLY_APPROVED','APPROVED','APPLIED','REJECTED','ROLLED_BACK')),
    rollback_reference VARCHAR(500) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (operations_approved_by IS NULL OR operations_approved_by <> requested_by),
    CHECK (performance_approved_by IS NULL OR (performance_approved_by <> requested_by AND performance_approved_by <> operations_approved_by))
);
