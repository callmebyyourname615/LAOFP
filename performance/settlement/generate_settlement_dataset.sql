\set tx_count 500000
\if :{?SETTLEMENT_TX_COUNT}
  \set tx_count :SETTLEMENT_TX_COUNT
\endif
\if :{?PERF_BUSINESS_DATE}
  \set business_date :'PERF_BUSINESS_DATE'
\else
  \set business_date CURRENT_DATE
\endif
BEGIN;
CREATE TEMP TABLE perf_seq AS SELECT generate_series(1, :tx_count) AS n;
INSERT INTO transactions (
    transaction_ref, client_transaction_id, idempotency_key,
    source_bank, source_account_no, destination_bank, destination_account_no,
    amount, currency, channel_id, status, reference, business_date,
    accepted_at, settled_at, created_at, updated_at, settlement_method)
SELECT
    'PERF-' || lpad(n::text, 12, '0'),
    'PERF-CLIENT-' || n,
    'PERF-IDEM-' || n,
    CASE WHEN n % 2 = 0 THEN 'BANK001' ELSE 'BANK002' END,
    '100' || n,
    CASE WHEN n % 2 = 0 THEN 'BANK002' ELSE 'BANK001' END,
    '200' || n,
    10000 + (n % 100000),
    'LAK',
    'API',
    'SETTLED',
    'performance settlement seed',
    :business_date::date,
    clock_timestamp() - ((n % 3600) || ' seconds')::interval,
    clock_timestamp() - ((n % 1800) || ' seconds')::interval,
    clock_timestamp(),
    clock_timestamp(),
    'DNS'
FROM perf_seq
ON CONFLICT DO NOTHING;
COMMIT;
ANALYZE transactions;
