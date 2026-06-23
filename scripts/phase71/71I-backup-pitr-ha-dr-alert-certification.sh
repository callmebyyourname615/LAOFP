#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71I 'Backup, PITR, HA, DR and alert certification'
for f in backup/bin/full-backup.sh backup/bin/verify-backup.sh backup/bin/restore-drill.sh dr/scripts/run-dr-suite.sh config/phase71-resilience-policy.yaml; do require_file "$f"; done
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'resilience and alert tooling ready; destructive UAT drills pending'; exit 0; fi
require_uat; require_flag PHASE71_EXECUTE_DR
: "${PHASE71_RESILIENCE_ATTESTATION:?PHASE71_RESILIENCE_ATTESTATION is required}"
phase_run 'full backup' backup/bin/full-backup.sh
phase_run 'verify backup' backup/bin/verify-backup.sh
phase_run 'restore and PITR drill' backup/bin/restore-drill.sh
for scenario in pod-kill kafka-fail net-partition s3-down ext-timeout; do phase_run "DR $scenario" dr/scripts/run-dr-suite.sh "$scenario"; done
if [[ "${PHASE71_EXECUTE_REGION_FAILOVER:-false}" == true ]]; then phase_run 'DR region failover' dr/scripts/run-dr-suite.sh region-failover; fi
phase_run 'verify resilience attestation' python3 scripts/phase71/verify_attestation.py --kind resilience-alerts --file "$PHASE71_RESILIENCE_ATTESTATION" --output "$PHASE71_DIR/resilience-attestation.json"
phase_finalize PASS 0 'backup, PITR, HA/DR, failback and critical alert lifecycle certified'
