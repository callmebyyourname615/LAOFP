-- ============================================================
-- V26 · Prefunded Pool (P13)
--
--   psp_pools         — one row per PSP; tracks balance + holds
--   (pool_transactions added in V27 for the per-operation audit trail)
-- ============================================================

CREATE TABLE psp_pools (
    pool_id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    psp_id                VARCHAR(32)   NOT NULL REFERENCES participants(bank_code) ON DELETE CASCADE,
    balance               DECIMAL(20,4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    held_amount           DECIMAL(20,4) NOT NULL DEFAULT 0 CHECK (held_amount >= 0),
    -- available_balance is the real-time tradeable balance; persisted so queries are O(1)
    available_balance     DECIMAL(20,4) GENERATED ALWAYS AS (balance - held_amount) STORED,
    currency              CHAR(3)       NOT NULL DEFAULT 'LAK',
    minimum_balance       DECIMAL(20,4) NOT NULL DEFAULT 100000000, -- LAK 100 M default
    alert_threshold_pct   DECIMAL(5,2)  NOT NULL DEFAULT 120.00,    -- fire alert at 120% of min
    last_alert_sent_at    TIMESTAMP(3)  NULL,    -- throttle: one alert per PSP per 15 min
    last_updated_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_psp_pools_psp_id UNIQUE (psp_id)
);

CREATE INDEX idx_psp_pools_psp_id ON psp_pools(psp_id);

-- Seed initial pool rows for all existing ACTIVE participants (balance starts at 0)
INSERT INTO psp_pools (psp_id, balance, held_amount, currency, minimum_balance)
SELECT bank_code, 0, 0, 'LAK', 100000000
FROM participants
WHERE status = 'ACTIVE'
ON CONFLICT (psp_id) DO NOTHING;
