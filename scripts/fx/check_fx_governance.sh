#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
stale=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM fx_governance_policy p WHERE p.enabled AND NOT EXISTS (SELECT 1 FROM governed_fx_rate_publication r WHERE r.currency_pair=p.currency_pair AND r.status='APPROVED' AND r.valid_until>now())")
[[ "$stale" == "0" ]] || { echo "missing/stale governed FX publications: $stale" >&2; exit 1; }
echo "FX governance control PASS"
