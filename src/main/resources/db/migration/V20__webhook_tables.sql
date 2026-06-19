-- ============================================================
-- V20 · Webhook & Notification Engine (P12)
--
--   webhook_registrations — PSP endpoint subscriptions
--   webhook_delivery_log  — per-delivery attempt tracking
-- ============================================================

-- ── webhook_registrations ─────────────────────────────────────
-- One row per PSP webhook endpoint.
-- A PSP can register multiple URLs for different event sets.
CREATE TABLE webhook_registrations (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_id        VARCHAR(36)  NOT NULL,       -- UUID, returned to caller
    psp_id            VARCHAR(32)  NOT NULL REFERENCES participants(bank_code),
    url               VARCHAR(500) NOT NULL,
    event_types       TEXT         NOT NULL,       -- JSON array e.g. ["TRANSFER.SETTLED"]
    secret_plain      VARCHAR(256) NOT NULL,       -- used to sign outbound payloads (KMS-encrypt in prod)
    secret_hash       VARCHAR(64)  NOT NULL,       -- SHA-256 of secret (display / rotation check)
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | PAUSED | FAILED
    failed_deliveries INT          NOT NULL DEFAULT 0,
    last_delivered_at TIMESTAMP(3) NULL,
    created_at        TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP(3) NULL,
    CONSTRAINT uq_webhook_registrations_id UNIQUE (webhook_id)
);

CREATE INDEX idx_webhook_reg_psp_status
    ON webhook_registrations(psp_id, status);

CREATE TRIGGER trg_webhook_registrations_updated_at
    BEFORE UPDATE ON webhook_registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── webhook_delivery_log ──────────────────────────────────────
-- Append-only.  One row per (webhook_registration × event fired).
-- Retry service picks up PENDING rows where attempt_count < 5.
CREATE TABLE webhook_delivery_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_id       VARCHAR(36)  NOT NULL REFERENCES webhook_registrations(webhook_id),
    event_type       VARCHAR(100) NOT NULL,
    event_ref        VARCHAR(80)  NULL,     -- e.g. transfer_ref for correlation
    payload          TEXT         NOT NULL, -- JSON body sent/to be sent
    attempt_count    INT          NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMP(3) NULL,
    next_retry_at    TIMESTAMP(3) NULL,
    response_status  INT          NULL,
    delivered_at     TIMESTAMP(3) NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | DELIVERED | FAILED_FINAL
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL
);

CREATE INDEX idx_webhook_delivery_pending
    ON webhook_delivery_log(status, next_retry_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_webhook_delivery_webhook_id
    ON webhook_delivery_log(webhook_id, created_at DESC);
CREATE INDEX idx_webhook_delivery_event_ref
    ON webhook_delivery_log(event_ref) WHERE event_ref IS NOT NULL;

CREATE TRIGGER trg_webhook_delivery_log_updated_at
    BEFORE UPDATE ON webhook_delivery_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
