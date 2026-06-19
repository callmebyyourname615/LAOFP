-- ============================================================
-- V8 · Settlement and Reconciliation tables
--
--   settlement_cycles      — intraday settlement cycles (1-4/day)
--   settlement_positions   — net position per participant per cycle
--   settlement_items       — individual items (partitioned by settlement_date)
--   reconciliation_files   — uploaded reconciliation files
--   reconciliation_items   — per-line items (partitioned by recon_date)
-- ============================================================

-- ── settlement_cycles ────────────────────────────────────────
CREATE TABLE settlement_cycles (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_ref        VARCHAR(40)  NOT NULL,
    settlement_date  DATE         NOT NULL,
    cycle_number     SMALLINT     NOT NULL,   -- 1-4 for intraday cycles
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    -- OPEN | CLOSED | SETTLED | FAILED
    opened_at        TIMESTAMP(3) NULL,
    closed_at        TIMESTAMP(3) NULL,
    settled_at       TIMESTAMP(3) NULL,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL,
    CONSTRAINT uq_settlement_cycles_ref    UNIQUE (cycle_ref),
    CONSTRAINT uq_settlement_cycles_date_no UNIQUE (settlement_date, cycle_number)
);

CREATE INDEX idx_settlement_cycles_date
    ON settlement_cycles(settlement_date, status);

CREATE TRIGGER trg_settlement_cycles_updated_at
    BEFORE UPDATE ON settlement_cycles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── settlement_positions ─────────────────────────────────────
-- Net position per participant per cycle.  One row per (cycle, bank).
CREATE TABLE settlement_positions (
    id                BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_id          BIGINT         NOT NULL REFERENCES settlement_cycles(id),
    bank_code         VARCHAR(32)    NOT NULL REFERENCES participants(bank_code),
    currency          VARCHAR(8)     NOT NULL DEFAULT 'LAK',
    debit_amount      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    credit_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    net_position      NUMERIC(18,2)  GENERATED ALWAYS AS (credit_amount - debit_amount) STORED,
    transaction_count INT            NOT NULL DEFAULT 0,
    status            VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    settled_at        TIMESTAMP(3)   NULL,
    created_at        TIMESTAMP(3)   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP(3)   NULL,
    CONSTRAINT uq_settlement_positions UNIQUE (cycle_id, bank_code, currency)
);

CREATE INDEX idx_settlement_positions_cycle
    ON settlement_positions(cycle_id);
CREATE INDEX idx_settlement_positions_bank
    ON settlement_positions(bank_code, status);

CREATE TRIGGER trg_settlement_positions_updated_at
    BEFORE UPDATE ON settlement_positions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── settlement_items (partitioned) ───────────────────────────
-- Individual transactions included in a settlement cycle.
CREATE TABLE settlement_items (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY,
    cycle_id        BIGINT        NOT NULL,
    bank_code       VARCHAR(32)   NOT NULL,   -- participant being settled
    transaction_ref VARCHAR(80)   NOT NULL,
    direction       VARCHAR(10)   NOT NULL,   -- DEBIT | CREDIT
    amount          NUMERIC(18,2) NOT NULL,
    currency        VARCHAR(8)    NOT NULL DEFAULT 'LAK',
    settlement_date DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, settlement_date)
) PARTITION BY RANGE (settlement_date);

CREATE INDEX idx_settlement_items_cycle
    ON settlement_items(cycle_id, settlement_date);
CREATE INDEX idx_settlement_items_bank
    ON settlement_items(bank_code, settlement_date);
CREATE INDEX idx_settlement_items_txn_ref
    ON settlement_items(transaction_ref, settlement_date);

-- ── reconciliation_files ─────────────────────────────────────
CREATE TABLE reconciliation_files (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_ref             VARCHAR(80)  NOT NULL,
    source_bank          VARCHAR(32)  NULL,
    file_name            VARCHAR(255) NOT NULL,
    file_type            VARCHAR(20)  NOT NULL DEFAULT 'LAOFP',   -- LAOFP | SWIFT | CUSTOM
    file_size_bytes      BIGINT       NULL,
    reconciliation_date  DATE         NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    -- RECEIVED | PROCESSING | COMPLETED | FAILED
    total_records        INT          NULL,
    matched_count        INT          NOT NULL DEFAULT 0,
    unmatched_count      INT          NOT NULL DEFAULT 0,
    object_key           VARCHAR(500) NULL,   -- path in object storage
    uploaded_by          VARCHAR(100) NULL,
    created_at           TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP(3) NULL,
    CONSTRAINT uq_reconciliation_files_ref UNIQUE (file_ref)
);

CREATE INDEX idx_recon_files_date
    ON reconciliation_files(reconciliation_date, status);
CREATE INDEX idx_recon_files_source_bank
    ON reconciliation_files(source_bank, reconciliation_date);

CREATE TRIGGER trg_reconciliation_files_updated_at
    BEFORE UPDATE ON reconciliation_files
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── reconciliation_items (partitioned) ───────────────────────
CREATE TABLE reconciliation_items (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY,
    file_id              BIGINT        NOT NULL,
    line_number          INT           NOT NULL,
    transaction_ref      VARCHAR(80)   NULL,
    external_ref         VARCHAR(100)  NULL,
    amount               NUMERIC(18,2) NULL,
    currency             VARCHAR(8)    NULL,
    match_status         VARCHAR(20)   NOT NULL DEFAULT 'UNMATCHED',
    -- MATCHED | UNMATCHED | DISPUTED
    mismatch_reason      TEXT          NULL,
    reconciliation_date  DATE          NOT NULL DEFAULT CURRENT_DATE,
    matched_at           TIMESTAMP(3)  NULL,
    created_at           TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, reconciliation_date)
) PARTITION BY RANGE (reconciliation_date);

CREATE INDEX idx_recon_items_file
    ON reconciliation_items(file_id, reconciliation_date);
CREATE INDEX idx_recon_items_txn_ref
    ON reconciliation_items(transaction_ref, reconciliation_date)
    WHERE transaction_ref IS NOT NULL;
CREATE INDEX idx_recon_items_match_status
    ON reconciliation_items(match_status, reconciliation_date);

-- ── Pre-create partitions for settlement_items and reconciliation_items ──
DO $$
DECLARE
    target_date DATE;
    next_date   DATE;
    pname       TEXT;
BEGIN
    FOR d IN -7..90 LOOP
        target_date := CURRENT_DATE + d;
        next_date   := target_date + 1;

        pname := 'settlement_items_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF settlement_items
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

        pname := 'reconciliation_items_' || TO_CHAR(target_date, 'YYYYMMDD');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF reconciliation_items
             FOR VALUES FROM (%L) TO (%L)',
            pname, target_date::TEXT, next_date::TEXT);

    END LOOP;
END;
$$;
