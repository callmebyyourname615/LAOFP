-- ============================================================
-- V7 · FPRE tables
--   reversal_log      — auto-reversal records (P10 FPRE feature)
--   psp_suspension_log — PSP suspension after repeated reversals
-- ============================================================

-- ── reversal_log ─────────────────────────────────────────────
CREATE TABLE reversal_log (
    reversal_id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_txn_id  VARCHAR(80)  NOT NULL,   -- maps to transactions.transaction_ref
    reversal_txn_id  VARCHAR(80)  NULL,
    destination_bank VARCHAR(32)  NOT NULL,
    reason           VARCHAR(30)  NOT NULL,   -- MAX_RETRIES | COMPLIANCE_BLOCK | EXPIRED
    status           VARCHAR(20)  NOT NULL DEFAULT 'INITIATED',
    failure_class    VARCHAR(40)  NULL,
    triggered_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP(3) NULL,
    created_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(3) NULL
);

CREATE INDEX idx_reversal_log_original_txn
    ON reversal_log(original_txn_id);
CREATE INDEX idx_reversal_log_destination_bank
    ON reversal_log(destination_bank, triggered_at);
CREATE INDEX idx_reversal_log_triggered_at
    ON reversal_log(triggered_at);

CREATE TRIGGER trg_reversal_log_updated_at
    BEFORE UPDATE ON reversal_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── psp_suspension_log ───────────────────────────────────────
CREATE TABLE psp_suspension_log (
    suspension_id   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    psp_id          VARCHAR(32)  NOT NULL REFERENCES participants(bank_code),
    suspended_at    TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    reversal_count  INT          NOT NULL,
    window_minutes  INT          NOT NULL DEFAULT 30,
    reinstated_at   TIMESTAMP(3) NULL,
    reinstated_by   VARCHAR(100) NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_psp_suspension_log_psp_id
    ON psp_suspension_log(psp_id);
CREATE INDEX idx_psp_suspension_log_suspended_at
    ON psp_suspension_log(suspended_at);
