-- Phase II-A completion: authorisation/settlement linkage and decline lifecycle.
ALTER TABLE rtp_request
    ADD COLUMN transfer_reference VARCHAR(128),
    ADD COLUMN settlement_reference VARCHAR(128),
    ADD COLUMN decline_reason VARCHAR(500),
    ADD COLUMN declined_at TIMESTAMPTZ,
    ADD COLUMN authorised_at TIMESTAMPTZ,
    ADD COLUMN settled_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_rtp_transfer_reference ON rtp_request(transfer_reference) WHERE transfer_reference IS NOT NULL;
CREATE UNIQUE INDEX uq_rtp_settlement_reference ON rtp_request(settlement_reference) WHERE settlement_reference IS NOT NULL;
ALTER TABLE rtp_request ADD CONSTRAINT ck_rtp_decline_fields CHECK (
    (status='DECLINED' AND declined_at IS NOT NULL AND decline_reason IS NOT NULL)
    OR status<>'DECLINED'
);
ALTER TABLE rtp_authorisation ADD COLUMN request_sha256 VARCHAR(64);
ALTER TABLE rtp_authorisation ADD CONSTRAINT ck_rtp_authorisation_sha CHECK (
    request_sha256 IS NULL OR request_sha256 ~ '^[0-9a-f]{64}$'
);

-- Phase II-D hardening: durable orchestrator request payload and TTL-aware idempotency.
ALTER TABLE push_payment_execution
    ADD COLUMN request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN idempotency_expires_at TIMESTAMPTZ,
    ADD COLUMN idempotency_active BOOLEAN NOT NULL DEFAULT true;

UPDATE push_payment_execution execution
   SET idempotency_expires_at = execution.started_at
       + make_interval(secs => policy.idempotency_ttl_seconds::integer)
  FROM push_payment_policy policy
 WHERE policy.id = execution.policy_id
   AND execution.idempotency_expires_at IS NULL;

ALTER TABLE push_payment_execution
    ALTER COLUMN idempotency_expires_at SET NOT NULL;

ALTER TABLE push_payment_execution
    DROP CONSTRAINT uq_push_execution_idempotency;

CREATE UNIQUE INDEX uq_push_execution_active_idempotency
    ON push_payment_execution(channel, idempotency_key)
    WHERE idempotency_active;

CREATE INDEX ix_push_execution_idempotency_expiry
    ON push_payment_execution(idempotency_active, idempotency_expires_at);

-- Preserve typed channel responses for idempotent replay and parity with Phase I APIs.
ALTER TABLE push_payment_execution
    ADD COLUMN result_message VARCHAR(500),
    ADD COLUMN result_payload JSONB;

-- Persist settlement routing and bounded retry metadata for RTP installment execution.
ALTER TABLE rtp_request
    ADD COLUMN settlement_inquiry_ref VARCHAR(64);

ALTER TABLE rtp_installment_schedule
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error_code VARCHAR(64);

CREATE INDEX ix_rtp_installment_retry
    ON rtp_installment_schedule(status, next_attempt_at, due_at);
