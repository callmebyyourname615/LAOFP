-- ============================================================
-- V35 · Settlement Reports
--
--   settlement_reports — per-PSP camt.054 BankToCustomerDebitCreditNotification
--                        generated automatically after each cycle settles.
-- ============================================================

CREATE TABLE settlement_reports (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_id      BIGINT       NOT NULL REFERENCES settlement_cycles(id),
    psp_id        VARCHAR(32)  NOT NULL,
    report_type   VARCHAR(20)  NOT NULL DEFAULT 'CAMT054',  -- CAMT054 | REGULATORY
    report_ref    VARCHAR(80)  NOT NULL,
    content       TEXT,
    generated_at  TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settlement_reports_ref         UNIQUE (report_ref),
    CONSTRAINT uq_settlement_reports_cycle_psp   UNIQUE (cycle_id, psp_id, report_type)
);

CREATE INDEX idx_settlement_reports_cycle
    ON settlement_reports(cycle_id);
CREATE INDEX idx_settlement_reports_psp
    ON settlement_reports(psp_id, generated_at DESC);
