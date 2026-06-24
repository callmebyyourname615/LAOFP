-- Phase 77C read-only continuous financial reconciliation controls.
-- All result values must be zero before GREEN readiness.

SELECT 'duplicate_transaction_reference' AS control,
       count(*) AS mismatch_count
FROM (
    SELECT transaction_ref, business_date
    FROM transactions
    GROUP BY transaction_ref, business_date
    HAVING count(*) > 1
) duplicates
UNION ALL
SELECT 'posted_control_journal_imbalance', count(*)
FROM (
    SELECT j.id,
           coalesce(sum(e.amount) FILTER (WHERE e.side = 'DEBIT'), 0) AS debit_total,
           coalesce(sum(e.amount) FILTER (WHERE e.side = 'CREDIT'), 0) AS credit_total
    FROM control_journal j
    LEFT JOIN control_journal_entry e ON e.journal_id = j.id
    WHERE j.status = 'POSTED'
    GROUP BY j.id
    HAVING coalesce(sum(e.amount) FILTER (WHERE e.side = 'DEBIT'), 0)
        <> coalesce(sum(e.amount) FILTER (WHERE e.side = 'CREDIT'), 0)
) imbalances
UNION ALL
SELECT 'settled_transaction_without_successful_outbox', count(*)
FROM transactions t
WHERE t.status = 'SETTLED'
  AND t.created_at >= now() - interval '24 hours'
  AND NOT EXISTS (
      SELECT 1 FROM outbox_messages o
      WHERE o.transaction_ref = t.transaction_ref
        AND o.status = 'SUCCESS'
  )
UNION ALL
SELECT 'stale_processing_outbox', count(*)
FROM outbox_messages
WHERE status = 'PROCESSING'
  AND locked_at < now() - interval '5 minutes'
UNION ALL
SELECT 'cross_border_reconciliation_mismatch', count(*)
FROM cross_border_rail_reconciliation
WHERE status <> 'MATCHED'
  AND created_at >= now() - interval '24 hours';
