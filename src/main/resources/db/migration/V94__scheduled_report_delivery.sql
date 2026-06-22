-- Phase II-E: scheduled report generation and delivery.
CREATE TABLE report_delivery_schedule (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    report_type VARCHAR(64) NOT NULL,
    recipient_participant_id VARCHAR(64) NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Vientiane',
    delivery_channel VARCHAR(24) NOT NULL,
    destination_config JSONB NOT NULL,
    retention_days INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    next_run_at TIMESTAMPTZ,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_report_channel CHECK (delivery_channel IN ('SFTP','S3','EMAIL_LINK')),
    CONSTRAINT ck_report_schedule_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    CONSTRAINT ck_report_retention CHECK (retention_days BETWEEN 1 AND 3650)
);
CREATE INDEX ix_report_schedule_due ON report_delivery_schedule(status, next_run_at);
CREATE TRIGGER trg_report_schedule_updated_at BEFORE UPDATE ON report_delivery_schedule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE report_artifact (
    id UUID PRIMARY KEY,
    report_type VARCHAR(64) NOT NULL,
    recipient_participant_id VARCHAR(64) NOT NULL,
    generation_key VARCHAR(160) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content BYTEA NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_report_artifact_generation UNIQUE(report_type, recipient_participant_id, generation_key),
    CONSTRAINT ck_report_artifact_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_report_artifact_size CHECK (size_bytes >= 0)
);

CREATE TABLE report_delivery_run (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES report_delivery_schedule(id) ON DELETE RESTRICT,
    scheduled_for TIMESTAMPTZ NOT NULL,
    artifact_id UUID REFERENCES report_artifact(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    delivered_at TIMESTAMPTZ,
    remote_reference VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_run_schedule_time UNIQUE(schedule_id, scheduled_for),
    CONSTRAINT ck_report_run_status CHECK (status IN ('QUEUED','GENERATED','DELIVERING','RETRY','DELIVERED','DEAD'))
);
CREATE INDEX ix_report_run_retry ON report_delivery_run(status, next_attempt_at);
CREATE TRIGGER trg_report_run_updated_at BEFORE UPDATE ON report_delivery_run
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE report_delivery_audit (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES report_delivery_run(id) ON DELETE RESTRICT,
    event_type VARCHAR(64) NOT NULL,
    event_payload JSONB NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_delivery_audit_sha CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);


INSERT INTO notification_template(template_code,purpose,contains_sensitive_data)
VALUES ('REPORT_READY','Scheduled report delivery notification',false)
ON CONFLICT(template_code) DO NOTHING;

INSERT INTO notification_template_version(id,template_code,version_no,channel,locale,subject_template,body_template,status,requested_by,approved_by,content_hash)
VALUES ('88000000-0000-0000-0000-000000000001','REPORT_READY',1,'EMAIL','en','Report ready','Your report {{fileName}} is ready. Download: {{downloadUrl}}. Link expires at {{expiresAt}}.','ACTIVE','phase-ii-bootstrap','phase-ii-bootstrap-approver','3e642fa13b99d8c088904c893cf2effca5220b91032d56a0c65e0957c6fa37b5')
ON CONFLICT(template_code,version_no,channel,locale) DO NOTHING;
