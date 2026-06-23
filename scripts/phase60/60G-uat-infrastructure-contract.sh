#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60G" "UAT infrastructure contract"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="UAT infrastructure contract failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "Kubernetes production contract delivery" python3 scripts/validate_production_environment.py --verify-k8s

if phase_is_preflight; then
  phase_run "production template contract" python3 scripts/validate_production_environment.py \
    --env-file .env.prod.example --template
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="environment contract and UAT probe are prepared; live endpoints were not contacted"
  exit 0
fi

[[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase_log "TARGET_ENVIRONMENT must equal uat"; exit 64; }
: "${UAT_ENV_FILE:?UAT_ENV_FILE is required}"
: "${UAT_BASE_URL:?UAT_BASE_URL is required}"

phase_run "resolved UAT environment contract" python3 scripts/validate_production_environment.py \
  --env-file "$UAT_ENV_FILE"

probe_args=(
  --env-file "$UAT_ENV_FILE"
  --base-url "$UAT_BASE_URL"
  --output "$PHASE60_PHASE_DIR/uat-infrastructure-probe.json"
)
if [[ -n "${UAT_HEALTH_HEADER_NAME:-}" && -n "${UAT_HEALTH_HEADER_VALUE:-}" ]]; then
  probe_args+=(--health-header-name "$UAT_HEALTH_HEADER_NAME")
fi
phase_run "live UAT infrastructure probe" python3 scripts/phase60/probe_uat_infrastructure.py "${probe_args[@]}"

if [[ "${PHASE60_DEEP_INFRA_CHECK:-false}" == "true" ]]; then
  phase_require_command psql
  phase_require_command kcat
  : "${DB_URL:?DB_URL is required for deep infrastructure check}"
  : "${DB_USERNAME:?DB_USERNAME is required for deep infrastructure check}"
  : "${DB_PASSWORD:?DB_PASSWORD is required for deep infrastructure check}"
  export PGPASSWORD="$DB_PASSWORD"
  phase_run "PostgreSQL authenticated probe" psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -c "SELECT 1"
  phase_run "Kafka metadata probe" kcat -L -b "$SPRING_KAFKA_BOOTSTRAP_SERVERS"
fi

PHASE_STATUS="PASS"
PHASE_MESSAGE="resolved configuration, TLS, application health, PostgreSQL, Kafka, Vault and object storage probes passed"
