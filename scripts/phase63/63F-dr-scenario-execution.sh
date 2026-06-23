#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63F 'Disaster-recovery and controlled failure scenario execution'
STATUS=FAIL; MESSAGE='DR scenario execution failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file dr/scripts/run-dr-suite.sh
phase63_require_file dr/scripts/verify-recovery.sh
phase63_require_file scripts/phase63/verify_dr_attestation.py
if ! phase63_is_full; then
  STATUS=PREPARED; MESSAGE='pod, Kafka, network, object-storage, external timeout and failover drills are ready'; exit 0
fi
phase63_require_uat_confirmation
: "${PHASE63_DR_ATTESTATION:?PHASE63_DR_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_DR_ATTESTATION"
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BASE_URL:?BASE_URL is required}"
export DR_ENVIRONMENT=uat
export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export EVIDENCE_DIR="$PHASE63_PHASE_DIR/runtime/dr"
mkdir -p "$EVIDENCE_DIR" "$PHASE63_PHASE_DIR/runtime/platform"
scenarios=(pod-kill kafka-fail s3-down net-partition ext-timeout deployment-rollback)
[[ "${PHASE63_INCLUDE_REGION_FAILOVER:-false}" == true ]] && scenarios+=(region-failover)
phase63_run 'controlled DR suite' dr/scripts/run-dr-suite.sh "${scenarios[@]}"
phase63_controlled_command postgres-failover "${PHASE63_POSTGRES_FAILOVER_COMMAND:-}" "$PHASE63_PHASE_DIR/runtime/platform/postgres-failover.log"
phase63_controlled_command postgres-failback "${PHASE63_POSTGRES_FAILBACK_COMMAND:-}" "$PHASE63_PHASE_DIR/runtime/platform/postgres-failback.log"
phase63_run 'DR evidence verification' python3 scripts/phase63/verify_dr_attestation.py \
  --attestation "$PHASE63_DR_ATTESTATION" --evidence-dir "$PHASE63_PHASE_DIR/runtime" \
  --output "$PHASE63_PHASE_DIR/dr-verification.json"
STATUS=PASS; MESSAGE='controlled failures recovered within signed RPO/RTO with zero transaction loss and idempotent replay'
