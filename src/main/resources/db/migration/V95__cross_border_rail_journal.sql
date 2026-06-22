-- Phase II-C: durable cross-border adapter journal and reconciliation.
CREATE TABLE cross_border_rail_message (
    id UUID PRIMARY KEY,
    rail VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    external_ref VARCHAR(160) NOT NULL,
    internal_ref VARCHAR(160) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    request_sha256 VARCHAR(64),
    response_sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    acknowledgement_code VARCHAR(64),
    last_error_code VARCHAR(64),
    settlement_date DATE,
    received_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cross_border_external UNIQUE(rail, direction, external_ref),
    CONSTRAINT ck_cross_border_direction CHECK (direction IN ('INBOUND','OUTBOUND')),
    CONSTRAINT ck_cross_border_rail CHECK (rail IN ('PROMPTPAY','BAKONG','NAPAS','UPI')),
    CONSTRAINT ck_cross_border_message_status CHECK (status IN ('RECEIVED','VALIDATED','PENDING','SUBMITTED','ACKNOWLEDGED','COMPLETED','DECLINED','FAILED','REPLAYED')),
    CONSTRAINT ck_cross_border_request_sha CHECK (request_sha256 IS NULL OR request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cross_border_response_sha CHECK (response_sha256 IS NULL OR response_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_cross_border_rail_status ON cross_border_rail_message(rail, status, created_at);
CREATE TRIGGER trg_cross_border_rail_message_updated_at BEFORE UPDATE ON cross_border_rail_message
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE cross_border_rail_reconciliation (
    id UUID PRIMARY KEY,
    rail VARCHAR(32) NOT NULL,
    statement_date DATE NOT NULL,
    external_ref VARCHAR(160) NOT NULL,
    internal_ref VARCHAR(160),
    external_amount NUMERIC(19,4) NOT NULL,
    internal_amount NUMERIC(19,4),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    discrepancy_reason VARCHAR(500),
    evidence_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cross_border_recon UNIQUE(rail, statement_date, external_ref),
    CONSTRAINT ck_cross_border_recon_status CHECK (status IN ('MATCHED','MISSING_INTERNAL','MISSING_EXTERNAL','AMOUNT_MISMATCH','CURRENCY_MISMATCH')),
    CONSTRAINT ck_cross_border_recon_sha CHECK (evidence_sha256 ~ '^[0-9a-f]{64}$')
);
