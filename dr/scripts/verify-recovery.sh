#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_dr_confirmation
: "${DB_URL:?}"
: "${DB_USERNAME:?}"
: "${DB_PASSWORD:?}"
: "${BASE_URL:?}"
export PGPASSWORD="$DB_PASSWORD"
record VERIFY START
curl --fail-with-body -sS "$BASE_URL/actuator/health" > "$EVIDENCE_DIR/health.json"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, -c "SELECT 'transactions',count(*) FROM transactions UNION ALL SELECT 'outbox_messages',count(*) FROM outbox_messages UNION ALL SELECT 'audit_logs',count(*) FROM audit_logs" > "$EVIDENCE_DIR/post-counts.csv"
python3 "$(dirname "$0")/verify-counts.py" "$EVIDENCE_DIR/baseline-counts.csv" "$EVIDENCE_DIR/post-counts.csv" > "$EVIDENCE_DIR/reconciliation.json"
psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, -c "SELECT 'duplicate_transaction_refs',COALESCE(SUM(c-1),0) FROM (SELECT count(*) c FROM transactions GROUP BY transaction_ref,business_date HAVING count(*)>1) d" > "$EVIDENCE_DIR/post-integrity.csv"
python3 - "$EVIDENCE_DIR/baseline-integrity.csv" "$EVIDENCE_DIR/post-integrity.csv" > "$EVIDENCE_DIR/replay-integrity.json" <<'PYINTEGRITY'
import csv, json, pathlib, sys

def load(path):
    with pathlib.Path(path).open(encoding="utf-8") as stream:
        return {row[0]: int(row[1]) for row in csv.reader(stream)}

baseline = load(sys.argv[1])
post = load(sys.argv[2])
base_count = baseline.get("duplicate_transaction_refs", 0)
post_count = post.get("duplicate_transaction_refs", 0)
new_duplicates = max(0, post_count - base_count)
document = {
    "schemaVersion": 1,
    "baselineDuplicateTransactionRefs": base_count,
    "postDuplicateTransactionRefs": post_count,
    "newDuplicateReplayCount": new_duplicates,
    "passed": new_duplicates == 0,
}
print(json.dumps(document, indent=2, sort_keys=True))
if not document["passed"]:
    raise SystemExit(1)
PYINTEGRITY
python3 - "$EVIDENCE_DIR/health.json" <<'PY'
import json, pathlib, sys
health = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if health.get("status") != "UP":
    raise SystemExit("application health is not UP after DR recovery")
PY
record VERIFY COMPLETE
