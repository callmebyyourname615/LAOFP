\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '120s';
SET LOCAL lock_timeout = '5s';
DO $$
BEGIN
  IF NOT pg_try_advisory_xact_lock(5602001) THEN
    RAISE EXCEPTION 'continuous reconciliation is already running';
  END IF;
END $$;
\copy (SELECT json_build_object(
  'capturedAt', to_char(clock_timestamp() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS"Z"'),
  'databaseTransactionId', txid_current(),
  'duplicateTransactions', (SELECT count(*) FROM (SELECT transaction_ref,business_date FROM transactions WHERE business_date >= current_date - 1 GROUP BY transaction_ref,business_date HAVING count(*) > 1) d),
  'unbalancedJournals', (SELECT count(*) FROM (SELECT j.id FROM control_journal j JOIN control_journal_entry e ON e.journal_id=j.id WHERE j.status='POSTED' GROUP BY j.id HAVING coalesce(sum(e.amount) FILTER (WHERE e.side='DEBIT'),0) <> coalesce(sum(e.amount) FILTER (WHERE e.side='CREDIT'),0)) u),
  'orphanOutboxMessages', (SELECT count(*) FROM transactions t WHERE t.business_date >= current_date - 1 AND t.status IN ('ACCEPTED','SETTLED') AND NOT EXISTS (SELECT 1 FROM outbox_messages o WHERE o.transaction_ref=t.transaction_ref)),
  'outboxBacklog', (SELECT count(*) FROM outbox_messages WHERE status IN ('PENDING','PROCESSING')),
  'oldestOutboxAgeSeconds', (SELECT coalesce(extract(epoch FROM (clock_timestamp()-min(created_at))),0)::bigint FROM outbox_messages WHERE status IN ('PENDING','PROCESSING')),
  'overdueWebhookDeliveries', (SELECT count(*) FROM webhook_delivery_log WHERE status='PENDING' AND coalesce(next_retry_at,created_at) < clock_timestamp()-interval '15 minutes'),
  'openReconciliationExceptions', (SELECT count(*) FROM reconciliation_exception_case WHERE status='OPEN'),
  'settledTransactionAmount', (SELECT coalesce(sum(amount),0) FROM transactions WHERE business_date >= current_date - 1 AND status='SETTLED'),
  'settledTransactionCount', (SELECT count(*) FROM transactions WHERE business_date >= current_date - 1 AND status='SETTLED')
)::text) TO STDOUT;
COMMIT;
