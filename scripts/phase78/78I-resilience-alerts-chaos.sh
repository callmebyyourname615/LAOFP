#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78I 'Backup PITR DR alerts and chaos certification'
missing=(); for f in scripts/phase73/run_phase73.sh backup/bin/full-backup.sh dr/scripts/run-dr-suite.sh; do [[ -f "$f" ]] || missing+=("$f"); done
if ((${#missing[@]})); then phase_finalize BLOCKED 2 "required resilience source missing: ${missing[*]}"; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'backup, PITR, DR, alerts and chaos gate ready'; exit 0; fi
require_uat; require_flag PHASE78_EXECUTE_DR; require_identity; : "${PHASE78_RESILIENCE_ATTESTATION:?required}"
[[ -n "${PHASE78_RESILIENCE_COMMAND:-}" ]] && phase_run 'resilience suite' bash -lc "$PHASE78_RESILIENCE_COMMAND"
phase_run 'resilience attestation' python3 scripts/phase78/verify_attestation.py --kind resilience-chaos --file "$PHASE78_RESILIENCE_ATTESTATION" --output "$PHASE78_DIR/resilience.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'resilience and chaos evidence passed'
