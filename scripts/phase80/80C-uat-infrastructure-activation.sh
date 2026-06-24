#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80C
if phase80_full; then
  phase80_require_env PHASE80_APP_BASE_URL PHASE80_PRIMARY_DB_URL PHASE80_REPLICA_DB_URL DB_USERNAME DB_PASSWORD || { phase80_emit BLOCKED 'UAT settings missing'; exit 1; }
  curl --fail-with-body -sS "$PHASE80_APP_BASE_URL/actuator/health" > "$PHASE80_EVIDENCE_ROOT/artifacts/app-health.json"
  export PGPASSWORD="$DB_PASSWORD"
  psql "$PHASE80_PRIMARY_DB_URL" -U "$DB_USERNAME" -AtX -c "select pg_is_in_recovery(), current_database(), now()" > "$PHASE80_EVIDENCE_ROOT/artifacts/primary-db.txt"
  psql "$PHASE80_REPLICA_DB_URL" -U "$DB_USERNAME" -AtX -c "select pg_is_in_recovery(), extract(epoch from now()-pg_last_xact_replay_timestamp())" > "$PHASE80_EVIDENCE_ROOT/artifacts/replica-db.txt"
  [[ "$(cut -d'|' -f1 "$PHASE80_EVIDENCE_ROOT/artifacts/primary-db.txt")" == f ]] || { phase80_emit BLOCKED 'primary is in recovery'; exit 1; }
  [[ "$(cut -d'|' -f1 "$PHASE80_EVIDENCE_ROOT/artifacts/replica-db.txt")" == t ]] || { phase80_emit BLOCKED 'replica is not in recovery'; exit 1; }
  [[ -z "${PHASE80_DEPENDENCY_PROBE_COMMAND:-}" ]] || bash -lc "$PHASE80_DEPENDENCY_PROBE_COMMAND" >> "$PHASE80_EVIDENCE_ROOT/logs/80C.log" 2>&1
  phase80_emit PASS 'UAT dependencies activated and database roles verified'
else phase80_emit PREPARED 'UAT probes configured; runtime endpoints not contacted'; fi
