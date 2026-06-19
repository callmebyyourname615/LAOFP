-- ============================================================
-- V27 · Prefunded Pool — Transaction Log (P13)
--
--   pool_transactions — immutable per-operation audit trail
--
--   operation values:
--     HOLD       — funds reserved for an in-flight transfer
--     CONFIRM    — hold converted to permanent debit (transfer settled)
--     RELEASE    — hold returned to available (transfer failed/reversed)
--     TOPUP      — balance increase (RTGS inbound)
--     ADJUSTMENT — manual BoL adjustment
-- ============================================================

CREATE TABLE pool_transactions (
    pool_txn_id    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_id        BIGINT        NOT NULL REFERENCES psp_pools(pool_id) ON DELETE CASCADE,
    txn_id         VARCHAR(80)   NOT NULL,   -- transfer_ref or topup ref
    operation      VARCHAR(15)   NOT NULL,
    amount         DECIMAL(20,4) NOT NULL CHECK (amount > 0),
    balance_before DECIMAL(20,4) NOT NULL,
    held_before    DECIMAL(20,4) NOT NULL,
    balance_after  DECIMAL(20,4) NOT NULL,
    held_after     DECIMAL(20,4) NOT NULL,
    occurred_at    TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    initiated_by   VARCHAR(60)   NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT chk_pool_txn_operation CHECK (
        operation IN ('HOLD', 'CONFIRM', 'RELEASE', 'TOPUP', 'ADJUSTMENT'))
);

CREATE INDEX idx_pool_txn_pool_id    ON pool_transactions(pool_id, occurred_at DESC);
CREATE INDEX idx_pool_txn_txn_id     ON pool_transactions(txn_id);
CREATE INDEX idx_pool_txn_operation  ON pool_transactions(operation, occurred_at DESC);
