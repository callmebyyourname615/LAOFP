#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74I 'Backup, PITR, HA/DR, alert and chaos certification'
if [[ ! -f scripts/phase73/run_phase73.sh ]]; then phase_finalize BLOCKED 2 'authoritative Phase 73 chaos source is missing'; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'resilience and real-chaos execution hooks ready'; exit 0; fi
require_uat; require_identity; require_flag PHASE74_EXECUTE_DR
: "${PHASE74_RESILIENCE_COMMAND:?PHASE74_RESILIENCE_COMMAND required}"
: "${PHASE74_RESILIENCE_ATTESTATION:?PHASE74_RESILIENCE_ATTESTATION required}"
phase_run 'backup, PITR, HA/DR and alerts' bash -lc "$PHASE74_RESILIENCE_COMMAND"
phase_run 'Phase 73 real UAT chaos certification' scripts/phase73/run_phase73.sh --full
phase_run 'resilience and chaos attestation' python3 scripts/phase74/verify_attestation.py --kind resilience-chaos --file "$PHASE74_RESILIENCE_ATTESTATION" --output "$PHASE74_DIR/resilience-chaos.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'RPO/RTO, failback, alert lifecycle and eight chaos experiments passed'
