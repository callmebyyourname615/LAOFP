\set ON_ERROR_STOP on
DO $$
DECLARE mismatch numeric; duplicates bigint; undelivered bigint;
BEGIN
  SELECT COALESCE(abs(sum(net_position)),0) INTO mismatch FROM settlement_positions;
  SELECT count(*) INTO duplicates FROM (
    SELECT transaction_ref, bank_code, direction, settlement_date
    FROM settlement_items GROUP BY 1,2,3,4 HAVING count(*)>1
  ) d;
  SELECT count(*) INTO undelivered
  FROM outbox_messages
  WHERE status IN ('FAILED','PENDING','PROCESSING') AND created_at < now()-interval '1 hour';
  IF mismatch<>0 OR duplicates<>0 OR undelivered<>0 THEN
    RAISE EXCEPTION 'financial mismatch=%, duplicate postings=%, stale outbox=%',mismatch,duplicates,undelivered;
  END IF;
END $$;
SELECT 'PHASE80_FINANCIAL_INTEGRITY_PASS';
