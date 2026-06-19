#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${PLAN_REFERENCE:?PLAN_REFERENCE is required}"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v plan="$PLAN_REFERENCE" -AtX <<'SQL'
WITH p AS (SELECT * FROM decommission_plan WHERE plan_reference=:'plan'),
checks AS (
 SELECT 'plan_exists',count(*)=1 AS ok FROM p
 UNION ALL SELECT 'fully_approved',count(*)=1 FROM p WHERE status='APPROVED' AND operations_approved_by IS NOT NULL AND risk_approved_by IS NOT NULL AND business_approved_by IS NOT NULL
 UNION ALL SELECT 'blocking_tasks_complete',count(*)=0 FROM decommission_task t JOIN p ON p.id=t.plan_id WHERE t.blocking AND t.status NOT IN ('DONE','WAIVED')
 UNION ALL SELECT 'data_exit_present',count(*)>0 FROM decommission_data_exit_artifact a JOIN p ON p.id=a.plan_id WHERE (NOT p.data_exit_required) OR a.encrypted
)
SELECT name,ok FROM checks ORDER BY name;
SQL
