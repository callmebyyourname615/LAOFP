-- ============================================================
-- V6 · Reliability tables (no partitioning — operational queues)
--
--   outbox_messages     — replaces outbox_events; active delivery queue
--   outbox_attempts     — per-attempt log for each outbox message
--   dead_letter_messages — permanently-failed messages
-- ============================================================

-- ── outbox_messages ──────────────────────────────────────────
-- Active delivery queue.  Rows are PENDING → PROCESSING → SUCCESS | FAILED.
-- After exhausting retries, the row moves to dead_letter_messages.
CREATE TABLE outbox_messages (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_ref VARCHAR(80)  NULL,   -- replaces transfer_ref
    inquiry_ref     VARCHAR(80)  NULL,
    message_type    VARCHAR(100) NOT NULL,   -- PACS_008 | CAMT_027 | etc.
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | PROCESSING | SUCCESS | FAILED | REVIEWED
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 5,
    failure_class   VARCHAR(40)  NULL,
    -- TRANSIENT | PERMANENT_BUSINESS | PERMANENT_COMPLIANCE | AMBIGUOUS
    will_retry      BOOLEAN      NOT NULL DEFAULT FALSE,
    last_error      VARCHAR(500) NULL,
    next_retry_at   TIMESTAMP(3) NULL,
    locked_at       TIMESTAMP(3) NULL,
    locked_by       VARCHAR(100) NULL,
    processed_at    TIMESTAMP(3) NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_messages_pending
    ON outbox_messages(status, next_retry_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_outbox_messages_processing
    ON outbox_messages(status, locked_at)
    WHERE status = 'PROCESSING';
CREATE INDEX idx_outbox_messages_txn_ref
    ON outbox_messages(transaction_ref) WHERE transaction_ref IS NOT NULL;
CREATE INDEX idx_outbox_messages_failure_class
    ON outbox_messages(failure_class) WHERE failure_class IS NOT NULL;

CREATE TRIGGER trg_outbox_messages_updated_at
    BEFORE UPDATE ON outbox_messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── outbox_attempts ──────────────────────────────────────────
-- Append-only log: one row per attempt for each outbox_message.
CREATE TABLE outbox_attempts (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outbox_message_id   BIGINT       NOT NULL,
    attempt_number      INT          NOT NULL,
    status              VARCHAR(20)  NOT NULL,   -- SUCCESS | FAILED
    error_code          VARCHAR(50)  NULL,
    error_message       VARCHAR(500) NULL,
    failure_class       VARCHAR(40)  NULL,
    connector_name      VARCHAR(128) NULL,
    duration_ms         INT          NULL,
    attempted_at        TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_attempts_message_id
    ON outbox_attempts(outbox_message_id, attempt_number);

-- ── dead_letter_messages ─────────────────────────────────────
-- Messages that exhausted all retries.  Stored permanently for
-- manual review and reprocessing.
CREATE TABLE dead_letter_messages (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_message_id BIGINT       NOT NULL,   -- FK to outbox_messages.id
    transaction_ref     VARCHAR(80)  NULL,
    message_type        VARCHAR(100) NOT NULL,
    payload             TEXT         NULL,
    failure_reason      VARCHAR(500) NULL,
    final_failure_class VARCHAR(40)  NULL,
    total_attempts      INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    reviewed_at         TIMESTAMP(3) NULL,
    reviewed_by         VARCHAR(100) NULL
);

CREATE INDEX idx_dead_letter_txn_ref
    ON dead_letter_messages(transaction_ref) WHERE transaction_ref IS NOT NULL;
CREATE INDEX idx_dead_letter_reviewed_at
    ON dead_letter_messages(reviewed_at);
