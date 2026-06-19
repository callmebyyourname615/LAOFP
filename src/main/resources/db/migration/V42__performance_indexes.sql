-- V42: Performance & Scale (P20) — additional indexes for 10,000 TPS
--
-- Existing indexes (already verified PASS):
--   outbox_messages(status, next_retry_at) WHERE status='PENDING'  → V6
--   vpa_registrations(vpa_type, vpa_value, status)                 → V29
--   sanctions_lists GIN(to_tsvector('simple', entity_name))        → V21
--   settlement_items(cycle_id), (bank_code), (transaction_ref)     → V8
--   transactions(settlement_method, business_date)                 → V33
--
-- New indexes added here:

-- ── transactions ──────────────────────────────────────────────────────────────
-- PSP-centric queries (dashboard, reconciliation, dispute raise):
--   SELECT ... FROM transactions WHERE source_bank = ? AND business_date BETWEEN ? AND ?
CREATE INDEX IF NOT EXISTS idx_txn_source_bank_date
    ON transactions(source_bank, business_date DESC)
    WHERE status = 'SETTLED';

CREATE INDEX IF NOT EXISTS idx_txn_dest_bank_date
    ON transactions(destination_bank, business_date DESC)
    WHERE status = 'SETTLED';

-- Dispute 90-day window check: WHERE transaction_ref = ? AND status = 'SETTLED'
-- transaction_ref is already indexed (primary key + unique).
-- created_at scan for window enforcement:
CREATE INDEX IF NOT EXISTS idx_txn_created_at
    ON transactions(created_at DESC);

-- ── disputes ──────────────────────────────────────────────────────────────────
-- SLA enforcement already has idx_disputes_sla.
-- PSP dispute history + status filter:
CREATE INDEX IF NOT EXISTS idx_disputes_responding_status
    ON disputes(responding_psp_id, status, sla_deadline)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

-- ── fx_quotes ─────────────────────────────────────────────────────────────────
-- Active-quote lookup by corridor (for rate display + expiry scan):
CREATE INDEX IF NOT EXISTS idx_fx_quotes_active
    ON fx_quotes(corridor_id, expires_at DESC)
    WHERE NOT used;

-- ── crossborder_transfers ─────────────────────────────────────────────────────
-- PSP cross-border history:
CREATE INDEX IF NOT EXISTS idx_cb_psp_date
    ON crossborder_transfers(initiating_psp_id, initiated_at DESC);

-- ── bill_payments ─────────────────────────────────────────────────────────────
-- Duplicate-check query: biller_id + bill_ref + CONFIRMED within 24h
-- Already has idx_bill_payments_biller_ref, but partial index on CONFIRMED is faster:
CREATE INDEX IF NOT EXISTS idx_bill_payments_confirmed
    ON bill_payments(biller_id, bill_ref, initiated_at DESC)
    WHERE status = 'CONFIRMED';

-- ── psp_pools ─────────────────────────────────────────────────────────────────
-- available_balance is a GENERATED ALWAYS AS column — O(1) read, no extra index needed.
-- Low-balance alert scan: WHERE available_balance < minimum_balance * alert_threshold_pct / 100
-- Use the existing uq_psp_pools_psp_id unique index for single-PSP reads.

-- ── ANALYZE hint ─────────────────────────────────────────────────────────────
-- Run after migration to update planner statistics:
ANALYZE transactions;
ANALYZE disputes;
ANALYZE fx_quotes;
ANALYZE crossborder_transfers;
ANALYZE bill_payments;
