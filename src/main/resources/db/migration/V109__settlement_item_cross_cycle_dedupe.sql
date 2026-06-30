-- Prevent the same transfer leg from being included in more than one cycle for
-- the same settlement date. Keep the earliest cycle item if UAT/manual retries
-- already produced duplicates, then rebuild affected positions from items.

CREATE TEMP TABLE tmp_settlement_position_status AS
SELECT cycle_id,
       bank_code,
       currency,
       status,
       settled_at
FROM settlement_positions;

WITH ranked AS (
    SELECT id,
           settlement_date,
           ROW_NUMBER() OVER (
               PARTITION BY transaction_ref, bank_code, direction, settlement_date
               ORDER BY cycle_id, id
           ) AS rn
    FROM settlement_items
)
DELETE FROM settlement_items si
USING ranked r
WHERE si.id = r.id
  AND si.settlement_date = r.settlement_date
  AND r.rn > 1;

DELETE FROM settlement_positions;

INSERT INTO settlement_positions
    (cycle_id, bank_code, currency, debit_amount, credit_amount, transaction_count, status, settled_at, updated_at)
SELECT si.cycle_id,
       si.bank_code,
       si.currency,
       SUM(CASE WHEN si.direction = 'DEBIT' THEN si.amount ELSE 0 END) AS debit_amount,
       SUM(CASE WHEN si.direction = 'CREDIT' THEN si.amount ELSE 0 END) AS credit_amount,
       COUNT(DISTINCT si.transaction_ref) AS transaction_count,
       COALESCE(MAX(old.status), 'OPEN') AS status,
       MAX(old.settled_at) AS settled_at,
       NOW() AS updated_at
FROM settlement_items si
LEFT JOIN tmp_settlement_position_status old
  ON old.cycle_id = si.cycle_id
 AND old.bank_code = si.bank_code
 AND old.currency = si.currency
GROUP BY si.cycle_id, si.bank_code, si.currency;

DROP TABLE tmp_settlement_position_status;

CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_items_transaction_leg_once_per_date
    ON settlement_items (transaction_ref, bank_code, direction, settlement_date);
