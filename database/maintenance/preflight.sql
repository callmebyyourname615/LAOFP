\set ON_ERROR_STOP on
DO $$
DECLARE
  long_transactions integer;
  invalid_indexes integer;
BEGIN
  IF pg_is_in_recovery() THEN
    RAISE EXCEPTION 'database maintenance must run on the primary';
  END IF;

  SELECT count(*) INTO long_transactions
  FROM pg_stat_activity
  WHERE pid <> pg_backend_pid()
    AND xact_start IS NOT NULL
    AND now() - xact_start > interval '5 minutes';
  IF long_transactions > 0 THEN
    RAISE EXCEPTION 'maintenance blocked by % long-running transaction(s)', long_transactions;
  END IF;

  SELECT count(*) INTO invalid_indexes
  FROM pg_index i
  JOIN pg_class c ON c.oid = i.indexrelid
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE NOT i.indisvalid AND n.nspname NOT IN ('pg_catalog','information_schema');
  IF invalid_indexes > 0 THEN
    RAISE EXCEPTION 'maintenance blocked by % invalid index(es)', invalid_indexes;
  END IF;
END $$;
