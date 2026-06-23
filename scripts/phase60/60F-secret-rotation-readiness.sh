#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60F" "Secret rotation and Git history purge closure"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="secret rotation closure failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "rotation inventory readiness" python3 scripts/phase60/verify_secret_rotation_attestation.py \
  --readiness-only --output "$PHASE60_PHASE_DIR/rotation-readiness.json"

if phase_is_preflight; then
  phase_run "worktree hygiene with pending deletions" security/scripts/verify-repository-hygiene.sh \
    --allow-pending-deletions --json-report "$PHASE60_PHASE_DIR/repository-hygiene.json"
  phase_require_file security/scripts/purge-sensitive-history.sh
  phase_require_file security/scripts/scan-git-history.sh
  phase_require_file security/policy/history-purge-paths.txt
  security/scripts/scan-git-history.sh --help > "$PHASE60_PHASE_DIR/history-scan-help.txt"
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="rotation inventory and safe purge tooling are ready; operator rotation, history rewrite and post-purge scan remain mandatory"
  exit 0
fi

phase_run "repository hygiene after committed deletions" security/scripts/verify-repository-hygiene.sh \
  --json-report "$PHASE60_PHASE_DIR/repository-hygiene.json"
: "${SECRET_ROTATION_ATTESTATION:?SECRET_ROTATION_ATTESTATION must point to the completed operator attestation}"
phase_run "completed rotation attestation" python3 scripts/phase60/verify_secret_rotation_attestation.py \
  --attestation "$SECRET_ROTATION_ATTESTATION" \
  --output "$PHASE60_PHASE_DIR/rotation-attestation-verification.json"
phase_run "full redacted Git history secret scan" security/scripts/scan-git-history.sh \
  --output-dir "$PHASE60_PHASE_DIR/full-history-scan"

PHASE_STATUS="PASS"
PHASE_MESSAGE="six credential rotations, history purge, cache/clone invalidation and post-purge scans are attested"
