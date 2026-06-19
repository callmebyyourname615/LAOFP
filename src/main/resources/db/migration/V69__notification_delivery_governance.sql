CREATE TABLE IF NOT EXISTS notification_template (
    template_code VARCHAR(100) PRIMARY KEY,
    purpose VARCHAR(200) NOT NULL,
    contains_sensitive_data BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS notification_template_version (
    id UUID PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL REFERENCES notification_template(template_code),
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','WEBHOOK','IN_APP')),
    locale VARCHAR(20) NOT NULL,
    subject_template TEXT,
    body_template TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ACTIVE','RETIRED','REJECTED')),
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(template_code,version_no,channel,locale),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_notification_template ON notification_template_version(template_code,channel,locale) WHERE status='ACTIVE';
CREATE TABLE IF NOT EXISTS notification_delivery (
    id UUID PRIMARY KEY,
    deduplication_key VARCHAR(200) NOT NULL UNIQUE,
    template_version_id UUID NOT NULL REFERENCES notification_template_version(id),
    recipient_reference_hash CHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED','SENDING','DELIVERED','RETRY','DEAD','CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    provider_reference VARCHAR(200),
    last_error_code VARCHAR(100),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_queue ON notification_delivery(status,next_attempt_at);
