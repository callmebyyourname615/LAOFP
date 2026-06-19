-- ============================================================
-- V23 · AML/CFT — Suspicious Transaction Reports (P19)
--
--   str_reports — STR lifecycle: PENDING_SUBMISSION → SUBMITTED → ACKNOWLEDGED
-- ============================================================

CREATE TABLE str_reports (
    str_id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_id          VARCHAR(80)  NOT NULL,
    triggered_at    TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    submitted_at    TIMESTAMP(3) NULL,
    submission_ref  VARCHAR(100) NULL,               -- BOL FIU acknowledgement ref
    status          VARCHAR(25)  NOT NULL DEFAULT 'PENDING_SUBMISSION',
    -- PENDING_SUBMISSION | SUBMITTED | ACKNOWLEDGED | SUBMISSION_FAILED
    report_payload  JSONB        NOT NULL,           -- STR JSON payload sent to BoL FIU
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT         NULL,               -- last submission error message
    matched_entity  TEXT         NULL,
    list_type       VARCHAR(10)  NULL,               -- BOL | OFAC | UN

    CONSTRAINT chk_str_status CHECK (status IN
        ('PENDING_SUBMISSION', 'SUBMITTED', 'ACKNOWLEDGED', 'SUBMISSION_FAILED'))
);

CREATE INDEX idx_str_txn_id     ON str_reports(txn_id);
CREATE INDEX idx_str_status     ON str_reports(status, triggered_at) WHERE status = 'PENDING_SUBMISSION';
CREATE INDEX idx_str_submitted  ON str_reports(submitted_at) WHERE status = 'SUBMITTED';
