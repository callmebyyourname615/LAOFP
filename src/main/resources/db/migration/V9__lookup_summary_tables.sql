-- ============================================================
-- V9 · Fast-lookup and summary tables (no partitioning)
--
--   transaction_lookup        — fast lookup by transaction_ref (cross-date)
--   inquiry_lookup            — fast lookup by inquiry_ref
--   hourly_transaction_summary
--   daily_transaction_summary
--   inquiry_daily_summary
-- ============================================================

-- ── transaction_lookup ───────────────────────────────────────
-- Maintained in sync with the transactions table.
-- Allows fast lookup by transaction_ref without knowing business_date,
-- then drill down to the partitioned table using business_date.
CREATE TABLE transaction_lookup (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_ref  VARCHAR(80)   NOT NULL,
    flow_ref         VARCHAR(80)   NULL,
    inquiry_ref      VARCHAR(80)   NULL,
    source_bank      VARCHAR(32)   NOT NULL,
    destination_bank VARCHAR(32)   NOT NULL,
    amount           NUMERIC(18,2) NOT NULL,
    currency         VARCHAR(8)    NOT NULL DEFAULT 'LAK',
    status           VARCHAR(30)   NOT NULL,
    business_date    DATE          NOT NULL,
    created_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3)  NULL,
    CONSTRAINT uq_transaction_lookup_ref UNIQUE (transaction_ref)
);

CREATE INDEX idx_txn_lookup_inquiry_ref
    ON transaction_lookup(inquiry_ref) WHERE inquiry_ref IS NOT NULL;
CREATE INDEX idx_txn_lookup_business_date
    ON transaction_lookup(business_date, status);
CREATE INDEX idx_txn_lookup_source
    ON transaction_lookup(source_bank, business_date);
CREATE INDEX idx_txn_lookup_destination
    ON transaction_lookup(destination_bank, business_date);

CREATE TRIGGER trg_transaction_lookup_updated_at
    BEFORE UPDATE ON transaction_lookup
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── inquiry_lookup ───────────────────────────────────────────
CREATE TABLE inquiry_lookup (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inquiry_ref      VARCHAR(80)   NOT NULL,
    flow_ref         VARCHAR(80)   NULL,
    source_bank      VARCHAR(32)   NOT NULL,
    destination_bank VARCHAR(32)   NOT NULL,
    creditor_account VARCHAR(34)   NULL,
    status           VARCHAR(30)   NOT NULL,
    business_date    DATE          NOT NULL,
    created_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3)  NULL,
    CONSTRAINT uq_inquiry_lookup_ref UNIQUE (inquiry_ref)
);

CREATE INDEX idx_inquiry_lookup_business_date
    ON inquiry_lookup(business_date, status);
CREATE INDEX idx_inquiry_lookup_source
    ON inquiry_lookup(source_bank, business_date);

CREATE TRIGGER trg_inquiry_lookup_updated_at
    BEFORE UPDATE ON inquiry_lookup
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── hourly_transaction_summary ───────────────────────────────
-- Maintained by a background aggregation job or DB trigger.
CREATE TABLE hourly_transaction_summary (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary_date     DATE          NOT NULL,
    hour_of_day      SMALLINT      NOT NULL,   -- 0-23
    source_bank      VARCHAR(32)   NOT NULL,
    destination_bank VARCHAR(32)   NOT NULL,
    currency         VARCHAR(8)    NOT NULL DEFAULT 'LAK',
    total_count      BIGINT        NOT NULL DEFAULT 0,
    settled_count    BIGINT        NOT NULL DEFAULT 0,
    rejected_count   BIGINT        NOT NULL DEFAULT 0,
    total_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    settled_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3)  NULL,
    CONSTRAINT uq_hourly_txn_summary
        UNIQUE (summary_date, hour_of_day, source_bank, destination_bank, currency)
);

CREATE INDEX idx_hourly_txn_summary_date
    ON hourly_transaction_summary(summary_date, hour_of_day);

CREATE TRIGGER trg_hourly_txn_summary_updated_at
    BEFORE UPDATE ON hourly_transaction_summary
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── daily_transaction_summary ────────────────────────────────
CREATE TABLE daily_transaction_summary (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary_date     DATE          NOT NULL,
    source_bank      VARCHAR(32)   NOT NULL,
    destination_bank VARCHAR(32)   NOT NULL,
    currency         VARCHAR(8)    NOT NULL DEFAULT 'LAK',
    total_count      BIGINT        NOT NULL DEFAULT 0,
    settled_count    BIGINT        NOT NULL DEFAULT 0,
    rejected_count   BIGINT        NOT NULL DEFAULT 0,
    reversed_count   BIGINT        NOT NULL DEFAULT 0,
    total_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    settled_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3)  NULL,
    CONSTRAINT uq_daily_txn_summary
        UNIQUE (summary_date, source_bank, destination_bank, currency)
);

CREATE INDEX idx_daily_txn_summary_date
    ON daily_transaction_summary(summary_date);

CREATE TRIGGER trg_daily_txn_summary_updated_at
    BEFORE UPDATE ON daily_transaction_summary
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── inquiry_daily_summary ─────────────────────────────────────
CREATE TABLE inquiry_daily_summary (
    id               BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary_date     DATE    NOT NULL,
    source_bank      VARCHAR(32) NOT NULL,
    destination_bank VARCHAR(32) NOT NULL,
    total_count      BIGINT  NOT NULL DEFAULT 0,
    completed_count  BIGINT  NOT NULL DEFAULT 0,
    failed_count     BIGINT  NOT NULL DEFAULT 0,
    expired_count    BIGINT  NOT NULL DEFAULT 0,
    eligible_count   BIGINT  NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL,
    CONSTRAINT uq_inquiry_daily_summary
        UNIQUE (summary_date, source_bank, destination_bank)
);

CREATE INDEX idx_inquiry_daily_summary_date
    ON inquiry_daily_summary(summary_date);

CREATE TRIGGER trg_inquiry_daily_summary_updated_at
    BEFORE UPDATE ON inquiry_daily_summary
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
