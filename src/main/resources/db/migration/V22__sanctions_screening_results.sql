-- ============================================================
-- V22 · AML/CFT — Sanctions Screening Results (P19)
--
--   sanctions_screening_results — per-transaction screening outcome
-- ============================================================

CREATE TABLE sanctions_screening_results (
    screen_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_id          VARCHAR(80)  NOT NULL,           -- transfer_ref or iso message_id
    screened_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    match_score     DECIMAL(5,2) NULL,               -- 0.00–100.00
    match_entity    TEXT         NULL,               -- matched entity name
    list_type       VARCHAR(10)  NULL,               -- BOL | OFAC | UN
    outcome         VARCHAR(15)  NOT NULL,           -- CLEAR | BLOCKED | MANUAL_REVIEW
    screening_ms    INT          NOT NULL DEFAULT 0, -- wall-clock time of screening call
    debtor_name     VARCHAR(500) NULL,
    creditor_name   VARCHAR(500) NULL,
    screened_by     VARCHAR(60)  NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT chk_screening_outcome CHECK (outcome IN ('CLEAR', 'BLOCKED', 'MANUAL_REVIEW'))
);

CREATE INDEX idx_screening_txn_id ON sanctions_screening_results(txn_id);
CREATE INDEX idx_screening_outcome ON sanctions_screening_results(outcome, screened_at DESC);
