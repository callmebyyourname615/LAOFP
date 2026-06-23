#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61I" "Backup, PITR, HA, DR and alert drills"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="resilience/alert certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "alert/runbook contract" python3 scripts/monitoring/verify_alert_runbooks.py
phase_require_file scripts/phase61/verify_resilience_evidence.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="backup/PITR, database/Kafka/Vault/object-store HA, DR and alert evidence contract is ready"; exit 0
fi
phase_require_uat_confirmation
: "${RESILIENCE_ATTESTATION:?RESILIENCE_ATTESTATION is required}"
export PHASE61_RESILIENCE_EVIDENCE_DIR="$PHASE61_PHASE_DIR/runtime-evidence"
phase_run "backup, PITR, HA, DR and alert certification" scripts/phase61/run_resilience_certification.sh
phase_run "resilience evidence verification" python3 scripts/phase61/verify_resilience_evidence.py \
  --attestation "$RESILIENCE_ATTESTATION" \
  --evidence-dir "$PHASE61_RESILIENCE_EVIDENCE_DIR" \
  --output "$PHASE61_PHASE_DIR/resilience-certification.json"
PHASE_STATUS="PASS"; PHASE_MESSAGE="backup/PITR, HA/DR, failback and critical alert routing achieved approved RPO/RTO with zero financial loss"
