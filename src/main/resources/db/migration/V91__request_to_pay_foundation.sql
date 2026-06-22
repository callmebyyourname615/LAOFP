-- Phase II-A: Request-to-Pay foundation.
-- This migration deliberately starts at V85 per PHASE_II_PLANNING.md.
-- V83/V84 are production-hardening predecessors and must be merged before
-- production certification. The Phase II baseline verifier reports if absent.

CREATE TABLE rtp_request (
    id UUID PRIMARY KEY,
    request_correlation_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    payee_participant_id VARCHAR(64) NOT NULL,
    payer_participant_id VARCHAR(64) NOT NULL,
    payee_account VARCHAR(128) NOT NULL,
    payer_account VARCHAR(128),
    requested_amount NUMERIC(19,4) NOT NULL,
    authorised_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    settled_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(280),
    status VARCHAR(32) NOT NULL,
    authorisation_mode VARCHAR(24),
    expires_at TIMESTAMPTZ NOT NULL,
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_rtp_request_participant_correlation UNIQUE (payee_participant_id, request_correlation_id),
    CONSTRAINT ck_rtp_request_correlation_format
        CHECK (request_correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$'),
    CONSTRAINT ck_rtp_request_fingerprint_sha256
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_rtp_request_amount_positive CHECK (requested_amount > 0),
    CONSTRAINT ck_rtp_request_amount_progress CHECK (
        authorised_amount >= 0
        AND settled_amount >= 0
        AND authorised_amount <= requested_amount
        AND settled_amount <= requested_amount
    ),
    CONSTRAINT ck_rtp_request_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_rtp_request_status CHECK (status IN (
        'PENDING_AUTH',
        'AUTHORISED',
        'PARTIALLY_SETTLED',
        'INSTALMENT_IN_PROGRESS',
        'SETTLED',
        'DECLINED',
        'EXPIRED',
        'CANCELLED'
    )),
    CONSTRAINT ck_rtp_request_authorisation_mode CHECK (
        authorisation_mode IS NULL OR authorisation_mode IN ('FULL','PARTIAL','INSTALLMENT')
    ),
    CONSTRAINT ck_rtp_request_cancel_fields CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED')
    )
);

CREATE INDEX ix_rtp_request_status_expiry
    ON rtp_request(status, expires_at);
CREATE INDEX ix_rtp_request_payee_created
    ON rtp_request(payee_participant_id, created_at DESC);
CREATE INDEX ix_rtp_request_payer_created
    ON rtp_request(payer_participant_id, created_at DESC);

CREATE TRIGGER trg_rtp_request_updated_at
    BEFORE UPDATE ON rtp_request
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE rtp_authorisation (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES rtp_request(id) ON DELETE RESTRICT,
    authorisation_reference VARCHAR(64) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    authorised_amount NUMERIC(19,4) NOT NULL,
    actor_participant_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_rtp_authorisation_reference UNIQUE (authorisation_reference),
    CONSTRAINT ck_rtp_authorisation_mode CHECK (mode IN ('FULL','PARTIAL','INSTALLMENT')),
    CONSTRAINT ck_rtp_authorisation_amount CHECK (authorised_amount > 0)
);

CREATE INDEX ix_rtp_authorisation_request
    ON rtp_authorisation(request_id, created_at);

CREATE TABLE rtp_installment_schedule (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES rtp_request(id) ON DELETE RESTRICT,
    installment_number INTEGER NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    transaction_reference VARCHAR(64),
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_rtp_installment_sequence UNIQUE (request_id, installment_number),
    CONSTRAINT uq_rtp_installment_transaction UNIQUE (transaction_reference),
    CONSTRAINT ck_rtp_installment_number CHECK (installment_number > 0),
    CONSTRAINT ck_rtp_installment_amount CHECK (amount > 0),
    CONSTRAINT ck_rtp_installment_status CHECK (status IN (
        'SCHEDULED','DUE','PROCESSING','SETTLED','FAILED','CANCELLED','EXPIRED'
    ))
);

CREATE INDEX ix_rtp_installment_due
    ON rtp_installment_schedule(status, due_at);

CREATE TRIGGER trg_rtp_installment_updated_at
    BEFORE UPDATE ON rtp_installment_schedule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE rtp_state_transition (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES rtp_request(id) ON DELETE RESTRICT,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_rtp_transition_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'PENDING_AUTH','AUTHORISED','PARTIALLY_SETTLED','INSTALMENT_IN_PROGRESS',
            'SETTLED','DECLINED','EXPIRED','CANCELLED'
        )
    ),
    CONSTRAINT ck_rtp_transition_to_status CHECK (to_status IN (
        'PENDING_AUTH','AUTHORISED','PARTIALLY_SETTLED','INSTALMENT_IN_PROGRESS',
        'SETTLED','DECLINED','EXPIRED','CANCELLED'
    ))
);

CREATE INDEX ix_rtp_transition_request_created
    ON rtp_state_transition(request_id, created_at);
