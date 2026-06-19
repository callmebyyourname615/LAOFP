#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55B; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A
live_require_command python3; live_require_command psql; live_require_command kubectl; live_require_command aws
: "${KAFKA_PROBE_SCRIPT:?KAFKA_PROBE_SCRIPT is required}"; [[ -x "$KAFKA_PROBE_SCRIPT" ]] || live_die "Kafka probe script must be executable"
: "${VAULT_ACCESS_PROBE_SCRIPT:?VAULT_ACCESS_PROBE_SCRIPT is required}"; [[ -x "$VAULT_ACCESS_PROBE_SCRIPT" ]] || live_die "Vault probe script must be executable"
: "${EXTERNAL_DEPENDENCY_PROBE_SCRIPT:?EXTERNAL_DEPENDENCY_PROBE_SCRIPT is required}"; [[ -x "$EXTERNAL_DEPENDENCY_PROBE_SCRIPT" ]] || live_die "external dependency probe must be executable"
: "${PRODUCTION_ENV_FILE:?PRODUCTION_ENV_FILE is required}"; [[ -f "$PRODUCTION_ENV_FILE" && ! -L "$PRODUCTION_ENV_FILE" ]] || live_die "production env file must be a regular file"
mode=$(stat -c %a "$PRODUCTION_ENV_FILE" 2>/dev/null || stat -f %Lp "$PRODUCTION_ENV_FILE"); (( 8#$mode <= 8#600 )) || live_die "production env file permissions must be 0600 or stricter"
phase_begin 55B "Production Infrastructure Contract"
failed=0
run_check cluster-context bash -c '
  ctx=$(kubectl config current-context)
  [[ "$ctx" == *prod* || "$ctx" == *production* ]] || { echo "context does not appear production" >&2; exit 64; }
  kubectl auth can-i get namespaces >/dev/null
' || failed=1
run_check infrastructure-contract python3 scripts/golive/verify_infrastructure_contract.py \
  --output "$PHASE_DIR/infrastructure-contract.json" --tls-output "$PHASE_DIR/tls-report.json" \
  --connectivity-output "$PHASE_DIR/connectivity-report.json" || failed=1
run_check database-primary-role bash -c '
  export PGPASSWORD="$DB_PASSWORD"; uri="${DB_URL#jdbc:}"
  psql "$uri" -X -v ON_ERROR_STOP=1 -At -c "SELECT CASE WHEN pg_is_in_recovery() THEN 1 ELSE 0 END" | grep -qx 0
' || failed=1
run_check database-replica-role bash -c '
  export PGPASSWORD="$DB_PASSWORD"; uri="${DB_REPLICA_URL#jdbc:}"
  psql "$uri" -X -v ON_ERROR_STOP=1 -At -c "SELECT CASE WHEN pg_is_in_recovery() THEN 1 ELSE 0 END" | grep -qx 1
' || failed=1
run_check database-replication-lag bash -c '
  export PGPASSWORD="$DB_PASSWORD"; uri="${DB_REPLICA_URL#jdbc:}"
  lag=$(psql "$uri" -X -v ON_ERROR_STOP=1 -At -c "SELECT COALESCE(EXTRACT(EPOCH FROM now()-pg_last_xact_replay_timestamp()),0)::int")
  test "$lag" -le "${MAX_DB_REPLICATION_LAG_SECONDS:-5}"
' || failed=1
run_check time-synchronization bash -c '
  if command -v timedatectl >/dev/null 2>&1; then timedatectl show -p NTPSynchronized --value | grep -qx yes || timedatectl show -p NTPSynchronized --value | grep -qx true;
  elif command -v chronyc >/dev/null 2>&1; then chronyc tracking | grep -q "Leap status.*Normal";
  else echo "no supported time synchronization verifier" >&2; exit 127; fi
' || failed=1
run_check kafka-authentication "$KAFKA_PROBE_SCRIPT" || failed=1
run_check vault-authentication-transit "$VAULT_ACCESS_PROBE_SCRIPT" || failed=1
run_check external-dependencies "$EXTERNAL_DEPENDENCY_PROBE_SCRIPT" || failed=1
run_check object-versioning bash -c '
  args=(); if [[ -n "${OBJECT_STORAGE_ENDPOINT:-}" ]]; then args=(--endpoint-url "$OBJECT_STORAGE_ENDPOINT"); fi
  status=$(aws "${args[@]}" s3api get-bucket-versioning --bucket "$OBJECT_STORAGE_BUCKET" --query Status --output text)
  [[ "$status" == Enabled ]]
' || failed=1
run_check object-lock bash -c '
  args=(); if [[ -n "${OBJECT_STORAGE_ENDPOINT:-}" ]]; then args=(--endpoint-url "$OBJECT_STORAGE_ENDPOINT"); fi
  status=$(aws "${args[@]}" s3api get-object-lock-configuration --bucket "$OBJECT_STORAGE_BUCKET" --query ObjectLockConfiguration.ObjectLockEnabled --output text)
  [[ "$status" == Enabled ]]
' || failed=1
run_check production-environment-contract bash -c 'python3 scripts/validate_production_environment.py --contract config/production-environment-contract.yaml --env-file "$1" --verify-k8s --root . > "$2"' _ "$PRODUCTION_ENV_FILE" "$PHASE_DIR/environment-contract.txt" || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
