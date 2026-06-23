#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60A" "Repository baseline and apply integrity"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="repository baseline failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_require_command git
phase_require_command python3
phase_run "repository baseline verifier" python3 scripts/phase60/verify_repository_baseline.py \
  --root "$PHASE60_ROOT" --output "$PHASE60_PHASE_DIR/repository-baseline.json"

git status --short > "$PHASE60_PHASE_DIR/git-status.txt"
git diff --name-status > "$PHASE60_PHASE_DIR/git-diff-name-status.txt"
git rev-parse HEAD > "$PHASE60_PHASE_DIR/git-commit.txt"

PHASE_STATUS="PASS"
PHASE_MESSAGE="repository baseline, migration inventory, required files and sensitive-path checks passed"
