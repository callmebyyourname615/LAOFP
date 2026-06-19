-- ============================================================
-- V24 · Risk Engine — Fraud Scores (P19)
--
--   fraud_scores — per-transaction risk evaluation result
-- ============================================================

CREATE TABLE fraud_scores (
    score_id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_id          VARCHAR(80)  NOT NULL,
    scored_at       TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    score           DECIMAL(5,4) NOT NULL,           -- 0.0000–1.0000 (normalised)
    risk_tier       VARCHAR(10)  NOT NULL,           -- LOW | MEDIUM | HIGH | CRITICAL
    signals         JSONB        NULL,               -- {"velocity_hit":true,"amount_anomaly":false,...}
    action_taken    VARCHAR(10)  NOT NULL,           -- ALLOW | FLAG | BLOCK
    sending_psp_id  VARCHAR(32)  NULL,
    receiving_psp_id VARCHAR(32) NULL,
    amount          DECIMAL(20,4) NULL,

    CONSTRAINT chk_fraud_risk_tier   CHECK (risk_tier   IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_fraud_action      CHECK (action_taken IN ('ALLOW', 'FLAG', 'BLOCK'))
);

CREATE INDEX idx_fraud_txn_id    ON fraud_scores(txn_id);
CREATE INDEX idx_fraud_risk_tier ON fraud_scores(risk_tier, scored_at DESC);
CREATE INDEX idx_fraud_psp       ON fraud_scores(sending_psp_id, scored_at DESC);
