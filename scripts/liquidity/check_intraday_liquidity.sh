#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
psql "$DB_URL" -v ON_ERROR_STOP=1 <<'SQL'
WITH expired AS (
 UPDATE liquidity_fund_reservation
    SET status='EXPIRED',completed_at=now()
  WHERE id IN (SELECT id FROM liquidity_fund_reservation WHERE status='RESERVED' AND expires_at<=now() FOR UPDATE SKIP LOCKED)
 RETURNING participant_code,currency,amount
), totals AS (
 SELECT participant_code,currency,sum(amount) amount FROM expired GROUP BY participant_code,currency
)
UPDATE participant_liquidity_control c
   SET reserved_balance=c.reserved_balance-t.amount,version=version+1,updated_at=now()
  FROM totals t
 WHERE c.participant_code=t.participant_code AND c.currency=t.currency AND c.reserved_balance>=t.amount;

WITH state AS (
 SELECT participant_code,currency,available_balance-reserved_balance AS headroom,warning_threshold,minimum_operating_balance
 FROM participant_liquidity_control
), inserted AS (
 INSERT INTO liquidity_control_breach(participant_code,currency,breach_type,headroom,evidence_hash)
 SELECT participant_code,currency,
        CASE WHEN headroom<minimum_operating_balance THEN 'MINIMUM_BREACH' ELSE 'WARNING_THRESHOLD' END,
        headroom,encode(digest(participant_code||'|'||currency||'|'||headroom::text||'|'||date_trunc('minute',now())::text,'sha256'),'hex')
 FROM state s
 WHERE headroom < greatest(warning_threshold,minimum_operating_balance)
   AND NOT EXISTS (SELECT 1 FROM liquidity_control_breach b WHERE b.participant_code=s.participant_code AND b.currency=s.currency AND b.resolved_at IS NULL)
 RETURNING 1
)
SELECT count(*) AS new_breaches FROM inserted;
SQL
