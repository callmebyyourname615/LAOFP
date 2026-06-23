\set ON_ERROR_STOP on
-- Read-only Phase 66 integrity checks. Required violation_count values must be zero.
BEGIN TRANSACTION READ ONLY;

SELECT 'flyway_failed_migrations' AS check_name, count(*) AS violation_count
FROM flyway_schema_history WHERE success = false;

SELECT 'duplicate_transaction_refs' AS check_name, count(*) AS violation_count
FROM (
  SELECT transaction_ref, business_date
  FROM transactions
  GROUP BY transaction_ref, business_date
  HAVING count(*) > 1
) d;

SELECT 'orphan_outbox_transactions' AS check_name, count(*) AS violation_count
FROM outbox_messages o
WHERE o.transaction_ref IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.transaction_ref = o.transaction_ref);

SELECT 'unbalanced_posted_control_journals' AS check_name, count(*) AS violation_count
FROM (
  SELECT j.id
  FROM control_journal j
  JOIN control_journal_entry e ON e.journal_id = j.id
  WHERE j.status IN ('POSTED','REVERSED')
  GROUP BY j.id
  HAVING COALESCE(sum(e.amount) FILTER (WHERE e.side='DEBIT'),0)
      <> COALESCE(sum(e.amount) FILTER (WHERE e.side='CREDIT'),0)
) x;

SELECT 'unbalanced_settlement_cycles' AS check_name, count(*) AS violation_count
FROM (
  SELECT cycle_id, currency
  FROM settlement_positions
  GROUP BY cycle_id, currency
  HAVING abs(COALESCE(sum(net_position),0)) > 0.0001
) x;

SELECT 'negative_reporting_counters' AS check_name,
       (SELECT count(*) FROM reporting.current_transaction_status WHERE total_count < 0)
     + (SELECT count(*) FROM reporting.current_inquiry_status WHERE total_count < 0)
     + (SELECT count(*) FROM reporting.current_outbox_status WHERE total_count < 0)
       AS violation_count;

SELECT 'database_recovery_state' AS check_name, 0 AS violation_count,
       pg_is_in_recovery() AS is_replica,
       CASE WHEN pg_is_in_recovery()
            THEN EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))
            ELSE 0 END AS replay_lag_seconds;

ROLLBACK;
