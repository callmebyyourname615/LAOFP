-- ============================================================
-- V4 · Core flow tables (all partitioned by business_date)
--
--   payment_flows              — top-level flow entity
--   inquiries                  — inquiry step (CAMT.027 / account lookup)
--   transactions               — payment transaction (PACS.008)
--   transaction_status_history — status change audit trail
--   transaction_events         — domain events
--   idempotency_records        — client deduplication (not partitioned)
--
-- PostgreSQL declarative PARTITION BY RANGE (business_date).
-- Primary keys include business_date so the partition constraint
-- is satisfied.  Unique constraints on natural keys also include
-- business_date.
--
-- Partitions are pre-created for:
--   7 days back  (covers recent test / seed data)
--   90 days forward (covers operational pre-creation window)
-- ============================================================

-- ── payment_flows ───────────────────────────────────────────
-- One row per end-to-end payment flow.
-- Ties together one inquiry (optional) and one transaction.
CREATE TABLE payment_flows (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    flow_ref         VARCHAR(80)  NOT NULL,   -- UUID v4 assigned by the API
    inquiry_ref      VARCHAR(80)  NULL,        -- FK to inquiries.inquiry_ref
    transaction_ref  VARCHAR(80)  NULL,        -- FK to transactions.transaction_ref
    source_bank      VARCHAR(32)  NOT NULL,
    destination_bank VARCHAR(32)  NOT NULL,
    channel_id       VARCHAR(50)  NOT NULL DEFAULT 'API',
    amount           NUMERIC(18,2) NOT NULL,
    currency         VARCHAR(8)   NOT NULL DEFAULT 'LAK',
    status           VARCHAR(30)  NOT NULL DEFAULT 'INITIATED',
    -- INITIATED | INQUIRY_PENDING | INQUIRY_COMPLETED | TRANSFER_PENDING
    -- | SETTLED | FAILED | REVERSED
    business_date    DATE         NOT NULL DEFAULT CURRENT_DATE,
    initiated_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    settled_at       TIMESTAMP(3) NULL,
    failed_at        TIMESTAMP(3) NULL,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL,
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_payment_flows_flow_ref
    ON payment_flows(flow_ref, business_date);
CREATE INDEX idx_payment_flows_status_date
    ON payment_flows(business_date, status);
CREATE INDEX idx_payment_flows_source
    ON payment_flows(source_bank, business_date);
CREATE INDEX idx_payment_flows_destination
    ON payment_flows(destination_bank, business_date);

CREATE TRIGGER trg_payment_flows_updated_at
    BEFORE UPDATE ON payment_flows
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── inquiries ───────────────────────────────────────────────
-- Account lookup / CAMT.027 inquiry.
-- Merges old `inquiries` + `iso_inquiries` tables.
CREATE TABLE inquiries (
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY,
    inquiry_ref             VARCHAR(80)  NOT NULL,
    client_inquiry_id       VARCHAR(128) NULL,
    idempotency_key         VARCHAR(255) NULL,
    flow_ref                VARCHAR(80)  NULL,
    -- ISO 20022 correlation fields
    message_id              VARCHAR(36)  NULL,
    instruction_id          VARCHAR(36)  NULL,
    end_to_end_id           VARCHAR(36)  NULL,
    -- participants
    source_bank             VARCHAR(32)  NOT NULL,
    destination_bank        VARCHAR(32)  NOT NULL,
    channel_id              VARCHAR(50)  NOT NULL DEFAULT 'API',
    route_code              VARCHAR(128) NULL,
    connector_name          VARCHAR(128) NULL,
    -- account details
    debtor_account          VARCHAR(34)  NULL,
    creditor_account        VARCHAR(34)  NOT NULL,
    destination_account_name VARCHAR(200) NULL,
    amount                  NUMERIC(18,2) NULL,
    currency                VARCHAR(8)   NOT NULL DEFAULT 'LAK',
    -- result
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | SENT | COMPLETED | FAILED | EXPIRED
    account_found           BOOLEAN      NULL,
    bank_available          BOOLEAN      NULL,
    eligible_for_transfer   BOOLEAN      NULL,
    error_code              VARCHAR(50)  NULL,
    error_message           VARCHAR(500) NULL,
    reference               VARCHAR(100) NULL,
    used_by_transaction_ref VARCHAR(80)  NULL,
    expires_at              TIMESTAMP(3) NULL,
    business_date           DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at              TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP(3) NULL,
    PRIMARY KEY (id, business_date),
    -- Unique per ISO channel + message within one business day.
    -- Enables ON CONFLICT DO NOTHING for concurrent ACMT.023 idempotency.
    -- NULL message_id (JSON path) never violates this constraint.
    CONSTRAINT uq_inquiries_channel_message_date
        UNIQUE (channel_id, message_id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_inquiries_inquiry_ref
    ON inquiries(inquiry_ref, business_date);
CREATE INDEX idx_inquiries_idempotency
    ON inquiries(idempotency_key, business_date) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_inquiries_creditor_account
    ON inquiries(creditor_account, business_date);
CREATE INDEX idx_inquiries_status_date
    ON inquiries(business_date, status);

CREATE TRIGGER trg_inquiries_updated_at
    BEFORE UPDATE ON inquiries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── transactions ─────────────────────────────────────────────
-- Individual payment transaction (replaces old `transfers` table).
CREATE TABLE transactions (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY,
    transaction_ref       VARCHAR(80)  NOT NULL,   -- replaces transfer_ref
    client_transaction_id VARCHAR(128) NULL,        -- replaces client_transfer_id
    idempotency_key       VARCHAR(255) NULL,
    flow_ref              VARCHAR(80)  NULL,
    inquiry_ref           VARCHAR(80)  NULL,
    -- participants & accounts
    source_bank           VARCHAR(32)  NOT NULL,
    source_account_no     VARCHAR(34)  NOT NULL,
    destination_bank      VARCHAR(32)  NOT NULL,
    destination_account_no VARCHAR(34) NOT NULL,
    destination_account_name VARCHAR(200) NULL,
    -- payment details
    amount                NUMERIC(18,2) NOT NULL,
    currency              VARCHAR(8)   NOT NULL DEFAULT 'LAK',
    channel_id            VARCHAR(50)  NOT NULL DEFAULT 'API',
    route_code            VARCHAR(128) NULL,
    connector_name        VARCHAR(128) NULL,
    -- state
    status                VARCHAR(30)  NOT NULL DEFAULT 'ACCEPTED',
    -- ACCEPTED | SETTLED | REJECTED | REFUND_REQUESTED | REFUNDED
    error_code            VARCHAR(50)  NULL,
    error_message         VARCHAR(500) NULL,
    external_reference    VARCHAR(100) NULL,
    reference             VARCHAR(100) NULL,
    business_date         DATE         NOT NULL DEFAULT CURRENT_DATE,
    accepted_at           TIMESTAMP(3) NULL,
    settled_at            TIMESTAMP(3) NULL,
    rejected_at           TIMESTAMP(3) NULL,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP(3) NULL,
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_transactions_txn_ref
    ON transactions(transaction_ref, business_date);
CREATE INDEX idx_transactions_idempotency
    ON transactions(idempotency_key, business_date) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_transactions_inquiry_ref
    ON transactions(inquiry_ref, business_date) WHERE inquiry_ref IS NOT NULL;
CREATE INDEX idx_transactions_status_date
    ON transactions(business_date, status);
CREATE INDEX idx_transactions_source
    ON transactions(source_bank, business_date);
CREATE INDEX idx_transactions_destination
    ON transactions(destination_bank, business_date);

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── transaction_status_history ───────────────────────────────
-- Append-only log of every status transition.
CREATE TABLE transaction_status_history (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    transaction_ref VARCHAR(80)  NOT NULL,
    from_status     VARCHAR(30)  NULL,
    to_status       VARCHAR(30)  NOT NULL,
    reason_code     VARCHAR(50)  NULL,
    actor           VARCHAR(100) NULL,
    business_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_txn_status_history_ref
    ON transaction_status_history(transaction_ref, business_date);

-- ── transaction_events ───────────────────────────────────────
-- Domain events for a transaction (SETTLED, REVERSED, etc.).
CREATE TABLE transaction_events (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    transaction_ref VARCHAR(80)  NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    payload         JSONB        NULL,
    actor           VARCHAR(100) NULL,
    business_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_txn_events_ref
    ON transaction_events(transaction_ref, business_date);
CREATE INDEX idx_txn_events_type_date
    ON transaction_events(event_type, business_date);

-- ── idempotency_records ──────────────────────────────────────
-- Not partitioned — rows are short-lived (cleanup by expires_at).
CREATE TABLE idempotency_records (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_id      VARCHAR(50)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NULL,
    transaction_ref VARCHAR(80)  NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PROCESSING',
    expires_at      TIMESTAMP(3) NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP(3) NULL,
    CONSTRAINT uq_idempotency_records UNIQUE (channel_id, idempotency_key)
);

CREATE INDEX idx_idempotency_records_expires_at
    ON idempotency_records(expires_at);

CREATE TRIGGER trg_idempotency_records_updated_at
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── inquiry_status_history ──────────────────────────────────
-- Append-only status audit trail for inquiries.
CREATE TABLE inquiry_status_history (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inquiry_ref VARCHAR(80)  NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    reason_code VARCHAR(50)  NULL,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inquiry_status_history_ref
    ON inquiry_status_history(inquiry_ref);

-- ============================================================
-- Pre-create daily partitions:  CURRENT_DATE-7 to CURRENT_DATE+90
-- Runs once at migration time.  A scheduler job (PartitionMaintenanceService)
-- creates additional future partitions daily.
-- ============================================================
DO $$
DECLARE
    target_date DATE;
    next_date   DATE;
    pname       TEXT;
BEGIN
    FOR d IN -7..90 LOOP
        target_date := CURRENT_DATE + d;
        next_date   := target_date + 1;

        -- payment_flows
        pname := 'payment_flows_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF payment_flows
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        -- inquiries
        pname := 'inquiries_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF inquiries
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        -- transactions
        pname := 'transactions_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF transactions
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        -- transaction_status_history
        pname := 'transaction_status_history_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF transaction_status_history
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        -- transaction_events
        pname := 'transaction_events_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF transaction_events
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

    END LOOP;
END;
$$;
