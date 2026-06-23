#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72G
if [[ "$PHASE72_MODE" != full ]]; then
  phase72_result "$phase" PREPARED "Backup/PITR/DR framework is ready; destructive operations are disabled in preflight"
  exit 0
fi
phase72_require_full "$phase" PHASE72_CONFIRM_BACKUP_RESTORE
phase72_require_full "$phase" PHASE72_CONFIRM_DR
required=(PHASE72_FULL_BACKUP_CMD PHASE72_VERIFY_BACKUP_CMD PHASE72_ISOLATED_RESTORE_CMD PHASE72_PITR_CMD PHASE72_DR_SUITE_CMD PHASE72_RESILIENCE_SUMMARY)
missing=(); for v in "${required[@]}"; do [[ -n "${!v:-}" ]] || missing+=("$v"); done
if ((${#missing[@]})); then phase72_result "$phase" BLOCKED "Operator backup/DR commands or summary are missing" --detail missing="${missing[*]}"; exit 2; fi
for item in full-backup:PHASE72_FULL_BACKUP_CMD verify-backup:PHASE72_VERIFY_BACKUP_CMD isolated-restore:PHASE72_ISOLATED_RESTORE_CMD pitr:PHASE72_PITR_CMD dr-suite:PHASE72_DR_SUITE_CMD; do
  name=${item%%:*}; var=${item##*:}; cmd=${!var}
  if ! phase72_run_logged "72G-$name" bash -lc "$cmd"; then phase72_result "$phase" FAIL "Operator command failed: $name"; exit 1; fi
done
normalized="$PHASE72_ARTIFACT_DIR/resilience-summary.json"
if python3 "$PHASE72_ROOT/scripts/phase72/validate_resilience_summary.py" --path "$PHASE72_RESILIENCE_SUMMARY" --output "$normalized" | tee "$PHASE72_LOG_DIR/72G-validate.log"; then
  phase72_result "$phase" PASS "Backup, isolated restore, PITR and all DR objectives passed"
else phase72_result "$phase" FAIL "Resilience evidence failed RPO/RTO, scenario or financial-integrity policy"; exit 1; fi
