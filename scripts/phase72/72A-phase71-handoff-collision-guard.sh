#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72A
fingerprint="$PHASE72_ARTIFACT_DIR/environment-fingerprint.txt"
{
  echo "git_commit=${PHASE72_GIT_SHA:-$(phase72_git_sha)}"
  echo "generated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "kernel=$(uname -sr 2>/dev/null || echo unknown)"
  echo "java=$(java -version 2>&1 | head -n1 || true)"
  echo "docker=$(docker --version 2>/dev/null || echo unavailable)"
} > "$fingerprint"
collision=0
if git -C "$PHASE72_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  while IFS= read -r path; do
    case "$path" in scripts/phase71/*|docs/phase71/*|config/phase71/*|schemas/phase71/*|evidence/phase71/*|AGENT/PHASE71*) echo "Protected Phase 71 path modified: $path" | tee -a "$PHASE72_LOG_DIR/72A-collision.log"; collision=1;; esac
  done < <(git -C "$PHASE72_ROOT" status --porcelain=v1 -uall | sed -E 's/^.. //' | sed -E 's/^.* -> //')
fi
if (( collision )); then phase72_result "$phase" BLOCKED "Phase 71-owned files are modified"; exit 2; fi
if phase72_has_phase71; then
  phase72_result "$phase" PASS "Phase 71 handoff markers present and no collision detected" --detail phase71Present=true
elif [[ "$PHASE72_MODE" == full ]]; then
  phase72_result "$phase" BLOCKED "Phase 71 is not present in this baseline" --detail phase71Present=false; exit 2
else
  phase72_result "$phase" PREPARED "Collision guard ready; supplied baseline does not contain Phase 71" --detail phase71Present=false
fi
