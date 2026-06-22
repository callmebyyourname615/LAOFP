-- Phase II-D: central push-payment policy and execution journal.
CREATE TABLE push_payment_policy (
    id UUID PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    policy_version INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    timeout_ms BIGINT NOT NULL,
    retry_schedule_seconds INTEGER[] NOT NULL DEFAULT '{}',
    finality_mode VARCHAR(24) NOT NULL,
    webhook_event_names JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_ttl_seconds BIGINT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_policy_version UNIQUE(channel, policy_version),
    CONSTRAINT ck_push_policy_channel CHECK (channel IN ('TRANSFER','QR','BILL','RTP','CROSS_BORDER')),
    CONSTRAINT ck_push_policy_status CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED')),
    CONSTRAINT ck_push_policy_finality CHECK (finality_mode IN ('IMMEDIATE','ASYNCHRONOUS')),
    CONSTRAINT ck_push_policy_timeout CHECK (timeout_ms BETWEEN 100 AND 300000),
    CONSTRAINT ck_push_policy_ttl CHECK (idempotency_ttl_seconds BETWEEN 60 AND 2592000),
    CONSTRAINT ck_push_policy_window CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE UNIQUE INDEX uq_push_policy_active_channel ON push_payment_policy(channel) WHERE status='ACTIVE';

CREATE TABLE push_payment_execution (
    id UUID PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    business_reference VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_sha256 VARCHAR(64) NOT NULL,
    policy_id UUID NOT NULL REFERENCES push_payment_policy(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL,
    external_reference VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_execution_idempotency UNIQUE(channel, idempotency_key),
    CONSTRAINT ck_push_execution_sha CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_push_execution_status CHECK (status IN ('STARTED','ACCEPTED','PENDING','SETTLED','REJECTED','FAILED','RETRY_SCHEDULED'))
);
CREATE INDEX ix_push_execution_retry ON push_payment_execution(status, next_attempt_at);
CREATE TRIGGER trg_push_execution_updated_at BEFORE UPDATE ON push_payment_execution
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE push_payment_transition (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES push_payment_execution(id) ON DELETE RESTRICT,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_push_transition_execution ON push_payment_transition(execution_id, occurred_at);

-- Safe defaults preserve Phase I behaviour while moving lifecycle ownership to one policy table.
INSERT INTO push_payment_policy(id,channel,policy_version,status,timeout_ms,retry_schedule_seconds,finality_mode,webhook_event_names,idempotency_ttl_seconds,valid_from,requested_by,approved_by)
VALUES
('87000000-0000-0000-0000-000000000001','TRANSFER',1,'ACTIVE',30000,ARRAY[30,60,120],'ASYNCHRONOUS','{"settled":"TRANSFER.SETTLED","rejected":"TRANSFER.REJECTED"}'::jsonb,86400,now(),'phase-ii-bootstrap','phase-ii-bootstrap'),
('87000000-0000-0000-0000-000000000002','QR',1,'ACTIVE',10000,ARRAY[]::integer[],'IMMEDIATE','{"settled":"QR.PAYMENT.COMPLETED"}'::jsonb,86400,now(),'phase-ii-bootstrap','phase-ii-bootstrap'),
('87000000-0000-0000-0000-000000000003','BILL',1,'ACTIVE',30000,ARRAY[30,60],'IMMEDIATE','{"settled":"BILL.PAYMENT.CONFIRMED"}'::jsonb,86400,now(),'phase-ii-bootstrap','phase-ii-bootstrap'),
('87000000-0000-0000-0000-000000000004','RTP',1,'ACTIVE',30000,ARRAY[30,60,120],'ASYNCHRONOUS','{"settled":"rtp.settled"}'::jsonb,604800,now(),'phase-ii-bootstrap','phase-ii-bootstrap'),
('87000000-0000-0000-0000-000000000005','CROSS_BORDER',1,'ACTIVE',60000,ARRAY[60,180,600],'ASYNCHRONOUS','{"settled":"CROSSBORDER.PAYMENT.COMPLETED"}'::jsonb,604800,now(),'phase-ii-bootstrap','phase-ii-bootstrap');
