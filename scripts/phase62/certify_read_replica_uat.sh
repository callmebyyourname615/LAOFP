#!/usr/bin/env bash
set -Eeuo pipefail

[[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { echo "ERROR: TARGET_ENVIRONMENT=uat is required" >&2; exit 64; }
command -v psql >/dev/null 2>&1 || { echo "ERROR: psql is required" >&2; exit 69; }
: "${PRIMARY_DB_PSQL_DSN:?PRIMARY_DB_PSQL_DSN is required; do not put passwords in command arguments}"
: "${READ_REPLICA_PSQL_DSN:?READ_REPLICA_PSQL_DSN is required; do not put passwords in command arguments}"

out_dir="${PHASE62_PHASE_DIR:-scripts/phase62/evidence/read-replica-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$out_dir"

query() {
  local dsn_var="$1" sql="$2"
  # Indirect expansion keeps the DSN out of xtrace and command logs.
  local dsn="${!dsn_var}"
  psql "$dsn" --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align --command "$sql"
}

primary_recovery="$(query PRIMARY_DB_PSQL_DSN 'SELECT pg_is_in_recovery()')"
replica_recovery="$(query READ_REPLICA_PSQL_DSN 'SELECT pg_is_in_recovery()')"
primary_read_only="$(query PRIMARY_DB_PSQL_DSN 'SHOW transaction_read_only')"
replica_read_only="$(query READ_REPLICA_PSQL_DSN 'SHOW transaction_read_only')"
replay_lag="$(query READ_REPLICA_PSQL_DSN "SELECT COALESCE(EXTRACT(EPOCH FROM now() - pg_last_xact_replay_timestamp())::text, '0')")"

[[ "$primary_recovery" == "f" ]] || { echo "ERROR: primary reports recovery mode: $primary_recovery" >&2; exit 1; }
[[ "$replica_recovery" == "t" ]] || { echo "ERROR: replica is not in recovery mode: $replica_recovery" >&2; exit 1; }

python3 - "$out_dir/read-replica-certification.json" \
  "$primary_recovery" "$replica_recovery" "$primary_read_only" "$replica_read_only" "$replay_lag" <<'PY'
import json, pathlib, sys
from datetime import datetime, timezone
path = pathlib.Path(sys.argv[1])
doc = {
    "schemaVersion": 1,
    "capturedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    "primary": {"pgIsInRecovery": sys.argv[2] == "t", "transactionReadOnly": sys.argv[4]},
    "replica": {"pgIsInRecovery": sys.argv[3] == "t", "transactionReadOnly": sys.argv[5],
                "replayLagSeconds": float(sys.argv[6])},
    "status": "PASS",
}
path.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n")
PY

echo "PASS: primary/replica roles verified; evidence=$out_dir/read-replica-certification.json"
