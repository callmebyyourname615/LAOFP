\set ON_ERROR_STOP on
SELECT current_database() AS restored_database;
SELECT current_setting('server_version_num')::integer >= 160000 AS postgres_16_or_newer;
SELECT NOT pg_is_in_recovery() AS recovery_completed;
SELECT count(*) > 0 AS flyway_history_present
  FROM flyway_schema_history
 WHERE success = true;
SELECT to_regclass('public.transactions') IS NOT NULL AS transactions_table_present;
SELECT to_regclass('public.outbox_messages') IS NOT NULL AS outbox_table_present;
SELECT count(*) AS public_table_count
  FROM information_schema.tables
 WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
