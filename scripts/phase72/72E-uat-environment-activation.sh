#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72E
required=(PHASE72_BASE_URL PHASE72_PRIMARY_DB_URL PHASE72_REPLICA_DB_URL PHASE72_PROMETHEUS_URL PHASE72_GRAFANA_URL PHASE72_KAFKA_CHECK_CMD PHASE72_MINIO_CHECK_CMD PHASE72_VAULT_CHECK_CMD)
missing=()
for v in "${required[@]}"; do
  [[ -n "${!v:-}" ]] || missing+=("$v")
done
if [[ "$PHASE72_MODE" != full ]]; then
  phase72_result "$phase" PREPARED "UAT dependency checks are ready; full endpoints are not contacted in preflight" --detail missingEnvironment="${missing[*]:-none}"
  exit 0
fi
phase72_require_full "$phase" PHASE72_CONFIRM_UAT
if ((${#missing[@]})); then
  phase72_result "$phase" BLOCKED "Required UAT environment variables are missing" --detail missingEnvironment="${missing[*]}"
  exit 2
fi
for tool in curl psql python3; do
  command -v "$tool" >/dev/null || { phase72_result "$phase" BLOCKED "$tool is required"; exit 2; }
done
checks="$PHASE72_ARTIFACT_DIR/dependency-checks.tsv"
: > "$checks"
check() {
  local name="$1"; shift
  if "$@" >>"$PHASE72_LOG_DIR/72E-$name.log" 2>&1; then
    printf '%s\ttrue\n' "$name" >> "$checks"
  else
    printf '%s\tfalse\n' "$name" >> "$checks"
  fi
}
check application_health curl -fsS "${PHASE72_BASE_URL%/}/actuator/health"
check application_readiness curl -fsS "${PHASE72_BASE_URL%/}/actuator/health/readiness"
check postgres_primary bash -c '[[ "$(psql "$1" -Atqc "select pg_is_in_recovery()")" == f ]]' _ "$PHASE72_PRIMARY_DB_URL"
check postgres_replica bash -c '[[ "$(psql "$1" -Atqc "select pg_is_in_recovery()")" == t ]]' _ "$PHASE72_REPLICA_DB_URL"
check postgres_replica_recovery psql "$PHASE72_REPLICA_DB_URL" -Atqc "select coalesce(now()-pg_last_xact_replay_timestamp(), interval '0 seconds') < interval '60 seconds'"
check kafka bash -lc "$PHASE72_KAFKA_CHECK_CMD"
check object_storage bash -lc "$PHASE72_MINIO_CHECK_CMD"
check vault bash -lc "$PHASE72_VAULT_CHECK_CMD"
check prometheus curl -fsS "${PHASE72_PROMETHEUS_URL%/}/-/ready"
check grafana curl -fsS "${PHASE72_GRAFANA_URL%/}/api/health"
if [[ "$PHASE72_BASE_URL" == https://* ]]; then
  host=$(python3 -c 'from urllib.parse import urlparse; import sys; print(urlparse(sys.argv[1]).hostname)' "$PHASE72_BASE_URL")
  check tls_expiry bash -c 'echo | openssl s_client -servername "$1" -connect "$1:443" 2>/dev/null | openssl x509 -checkend 604800 -noout' _ "$host"
else
  printf 'tls_expiry\ttrue\n' >> "$checks"
fi
if [[ -n "${PHASE72_TIME_SYNC_CHECK_CMD:-}" ]]; then
  check time_synchronization bash -lc "$PHASE72_TIME_SYNC_CHECK_CMD"
else
  check time_synchronization bash -c 'command -v timedatectl >/dev/null && [[ "$(timedatectl show -p NTPSynchronized --value 2>/dev/null)" == true ]]'
fi
json="$PHASE72_ARTIFACT_DIR/dependency-result.json"
python3 - "$checks" "$json" <<'PY'
import json
import sys
checks = {}
with open(sys.argv[1], encoding="utf-8") as handle:
    for line in handle:
        key, value = line.rstrip().split("\t")
        checks[key] = value == "true"
with open(sys.argv[2], "w", encoding="utf-8") as handle:
    json.dump({"schemaVersion": 1, "checks": checks, "passed": all(checks.values())}, handle, indent=2, sort_keys=True)
    handle.write("\n")
PY
if python3 -c 'import json,sys; raise SystemExit(0 if json.load(open(sys.argv[1]))["passed"] else 1)' "$json"; then
  phase72_result "$phase" PASS "All required UAT dependencies passed"
else
  phase72_result "$phase" FAIL "One or more UAT dependencies failed"
  exit 1
fi
