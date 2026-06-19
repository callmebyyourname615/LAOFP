#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${RULE_FILE:?RULE_FILE required}"
python3 scripts/fraud/validate_velocity_rules.py "$RULE_FILE"
psql "$DB_URL" -v ON_ERROR_STOP=1 -c "\copy fraud_velocity_rule(rule_code,description,subject_type,window_seconds,max_count,max_amount,currency,action,enabled) FROM '${RULE_FILE}' CSV HEADER"
