#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${BUSINESS_DATE:?BUSINESS_DATE required, YYYY-MM-DD}"
OUT="${EVIDENCE_DIR:-evidence/reconciliation}/${BUSINESS_DATE}"
mkdir -p "$OUT"
psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "select count(*), coalesce(sum(amount),0) from transfers where business_date='${BUSINESS_DATE}'" > "$OUT/transfers.count"
psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "select count(*), coalesce(sum(amount),0) from settlement_entries where business_date='${BUSINESS_DATE}'" > "$OUT/settlement.count"
sha256sum "$OUT"/* > "$OUT/SHA256SUMS"
python3 scripts/reconciliation/compare_reconciliation_counts.py "$OUT/transfers.count" "$OUT/settlement.count" | tee "$OUT/result.json"
