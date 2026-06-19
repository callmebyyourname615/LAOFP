#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${SUBJECT_REFERENCE:?SUBJECT_REFERENCE required}"
OUT="${OUTPUT_DIR:-evidence/privacy}/${SUBJECT_REFERENCE}"
mkdir -p "$OUT"
psql "$DB_URL" -v ON_ERROR_STOP=1 --csv -c "select transaction_reference, amount, status, created_at from transfers where debtor_account_hash='${SUBJECT_REFERENCE}' or creditor_account_hash='${SUBJECT_REFERENCE}'" > "$OUT/transactions.csv"
sha256sum "$OUT/transactions.csv" > "$OUT/SHA256SUMS"
