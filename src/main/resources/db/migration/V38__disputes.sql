-- V38: Dispute & Refund Manager (P18)

-- ── disputes ──────────────────────────────────────────────────────────────────
CREATE TABLE disputes (
    dispute_id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_ref             VARCHAR(200)  NOT NULL,          -- original transaction_ref
    raising_psp_id      VARCHAR(32)   NOT NULL,
    responding_psp_id   VARCHAR(32)   NOT NULL,
    dispute_type        VARCHAR(30)   NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    raised_at           TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    sla_deadline        TIMESTAMP(3)  NOT NULL,
    resolved_at         TIMESTAMP(3),
    evidence            TEXT          NOT NULL DEFAULT '[]',  -- JSON array
    resolution_note     TEXT,
    auto_ruled          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP(3)  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dispute_type CHECK (dispute_type IN (
        'NOT_RECEIVED','WRONG_AMOUNT','DUPLICATE_CHARGE',
        'FRAUD','MERCHANT_DISPUTE','TECHNICAL_ERROR')),
    CONSTRAINT chk_dispute_status CHECK (status IN (
        'OPEN','UNDER_REVIEW','RESOLVED_REFUND',
        'RESOLVED_NO_ACTION','ESCALATED','CLOSED'))
);

CREATE INDEX idx_disputes_txn_ref    ON disputes(txn_ref);
CREATE INDEX idx_disputes_raising    ON disputes(raising_psp_id, status);
CREATE INDEX idx_disputes_responding ON disputes(responding_psp_id, status);
CREATE INDEX idx_disputes_sla        ON disputes(sla_deadline) WHERE status = 'OPEN';

-- ── refund_transactions ───────────────────────────────────────────────────────
CREATE TABLE refund_transactions (
    refund_id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_id          BIGINT        REFERENCES disputes(dispute_id),
    original_txn_ref    VARCHAR(200)  NOT NULL,
    refund_txn_ref      VARCHAR(200),
    amount              DECIMAL(20,4) NOT NULL,
    status              VARCHAR(10)   NOT NULL DEFAULT 'INITIATED',
    initiated_at        TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP(3),

    CONSTRAINT chk_refund_status CHECK (status IN ('INITIATED','COMPLETED','FAILED'))
);

CREATE INDEX idx_refund_dispute ON refund_transactions(dispute_id);
