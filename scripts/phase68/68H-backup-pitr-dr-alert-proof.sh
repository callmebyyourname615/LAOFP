#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68H 'Backup, PITR, DR and alert proof'
for f in backup/bin/full-backup.sh backup/bin/verify-backup.sh backup/bin/restore-drill.sh dr/scripts/run-dr-suite.sh dr/scripts/verify-recovery.sh; do require_file "$f"; done
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'resilience tooling present; destructive UAT drills pending'; exit 0; fi
require_uat
[[ "${PHASE68_EXECUTE_DR:-false}" == true ]] || { phase_log 'PHASE68_EXECUTE_DR=true required'; exit 64; }
: "${PHASE68_RESILIENCE_ATTESTATION:?PHASE68_RESILIENCE_ATTESTATION is required}"
export EVIDENCE_DIR="$PHASE68_DIR/dr"
phase_run 'full backup' backup/bin/full-backup.sh
phase_run 'verify backup' backup/bin/verify-backup.sh
phase_run 'restore and PITR drill' backup/bin/restore-drill.sh
phase_run 'DR scenario suite' env CONFIRM_DR_DRILL=yes DR_SCENARIOS='pod-kill kafka-fail net-partition s3-down ext-timeout' dr/scripts/run-dr-suite.sh
if [[ -n "${PHASE68_ALERT_DRILL_COMMAND:-}" ]]; then phase_run 'critical alert lifecycle drill' bash -lc "$PHASE68_ALERT_DRILL_COMMAND"; else phase_log 'PHASE68_ALERT_DRILL_COMMAND is required'; exit 64; fi
phase_run 'verify resilience attestation' python3 scripts/phase68/verify_attestation.py --kind resilience --file "$PHASE68_RESILIENCE_ATTESTATION" --output "$PHASE68_DIR/resilience-attestation-verification.json"
phase_finalize PASS 0 'backup, PITR, failover/failback, DR and critical alerts passed'
