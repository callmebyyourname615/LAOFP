#!/usr/bin/env bash
set -Eeuo pipefail
: "${PHASE67_MODE:=preflight}"
export PHASE67_MODE
if [[ "$PHASE67_MODE" != preflight ]]; then
  printf 'For import/execute mode, run each phase explicitly after its required Phase 55 evidence is available.\n' >&2
  exit 2
fi
: "${RELEASE_REFERENCE:=PHASE67-PREFLIGHT}"
: "${RELEASE_RC_ID:=switching-phase67-preflight}"
: "${RELEASE_GIT_COMMIT:=$(printf 'a%.0s' {1..40})}"
: "${RELEASE_APP_IMAGE_DIGEST:=sha256:$(printf 'b%.0s' {1..64})}"
: "${RELEASE_MIGRATION_IMAGE_DIGEST:=sha256:$(printf 'c%.0s' {1..64})}"
export RELEASE_REFERENCE RELEASE_RC_ID RELEASE_GIT_COMMIT RELEASE_APP_IMAGE_DIGEST RELEASE_MIGRATION_IMAGE_DIGEST
export PHASE67_RERUN_CONFIRMATION=I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT
for item in \
  '67A release scripts/phase67/67A-release-identity-freeze-gate.sh' \
  '67B production scripts/phase67/67B-production-infrastructure-gate.sh' \
  '67C release scripts/phase67/67C-immutable-rc-provenance.sh' \
  '67D production scripts/phase67/67D-financial-cutover-baseline.sh' \
  '67E production scripts/phase67/67E-canary-health-gate.sh' \
  '67F production scripts/phase67/67F-progressive-traffic-gate.sh' \
  '67G production scripts/phase67/67G-rollback-decision-engine.sh' \
  '67H production scripts/phase67/67H-command-center-recorder.sh' \
  '67I hypercare scripts/phase67/67I-hypercare-tracker.sh' \
  '67J hypercare scripts/phase67/67J-bau-acceptance-bundle.sh'
do
  read -r phase env script <<<"$item"
  printf '==> %s (%s)\n' "$phase" "$env"
  PHASE67_ENVIRONMENT="$env" "$script"
done
printf 'Phase 67A-67J preflight completed.\n'
