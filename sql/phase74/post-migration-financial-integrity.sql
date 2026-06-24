\set ON_ERROR_STOP on
-- Phase 74B read-only assertions. Each query must return zero rows or a true predicate.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM transaction_events GROUP BY transaction_id, event_type, sequence_no HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'duplicate transaction event sequence detected';
  END IF;
END $$;

DO $$
BEGIN
  IF to_regclass('public.settlement_ledger_entries') IS NOT NULL AND EXISTS (
    SELECT 1 FROM settlement_ledger_entries GROUP BY settlement_cycle_id
    HAVING COALESCE(sum(CASE WHEN direction = 'DEBIT' THEN amount ELSE 0 END),0)
        <> COALESCE(sum(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END),0)
  ) THEN
    RAISE EXCEPTION 'settlement debit/credit imbalance detected';
  END IF;
END $$;

DO $$
BEGIN
  IF to_regclass('public.transaction_status_counters') IS NOT NULL AND EXISTS (
    SELECT 1 FROM transaction_status_counters
    WHERE accepted_count < 0 OR rejected_count < 0 OR pending_count < 0
  ) THEN
    RAISE EXCEPTION 'negative transaction status counter detected';
  END IF;
END $$;
