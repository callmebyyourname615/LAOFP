-- ============================================================
-- V33 · Transfer settlement routing markers
--
--   Marks transfers that must bypass DNS netting and be settled
--   through RTGS because the amount crosses the configured threshold.
-- ============================================================

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS settlement_method VARCHAR(16) NOT NULL DEFAULT 'DNS',
    ADD COLUMN IF NOT EXISTS high_value BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_transactions_settlement_method_date
    ON transactions(settlement_method, business_date, status);
