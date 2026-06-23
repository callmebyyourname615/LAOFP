-- Phase 72 post-restore financial integrity checks. Run read-only on the isolated restored database.
WITH ledger_balance AS (
    SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END), 0) AS mismatch
    FROM ledger_entries
), outbox_state AS (
    SELECT COUNT(*) FILTER (WHERE status IN ('FAILED','PENDING','PROCESSING')) AS undelivered
    FROM outbox_messages
), settlement_balance AS (
    SELECT COALESCE(ABS(SUM(net_position)), 0) AS mismatch
    FROM settlement_positions
)
SELECT json_build_object(
    'ledgerMismatch', (SELECT mismatch FROM ledger_balance),
    'undeliveredOutbox', (SELECT undelivered FROM outbox_state),
    'settlementMismatch', (SELECT mismatch FROM settlement_balance)
)::text;
