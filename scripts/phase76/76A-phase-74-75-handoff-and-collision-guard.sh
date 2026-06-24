#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
missing=()
for d in scripts/phase74 scripts/phase75; do [[ -d "$ROOT/$d" ]] || missing+=("$d"); done
if ((${#missing[@]})); then
  if [[ "${PHASE76_MODE:-preflight}" == full ]]; then
    details=$(printf '%s\n' "${missing[@]}" | python3 -c 'import json,sys;print(json.dumps({"missing":sys.stdin.read().split()}))')
    write_result 76A BLOCKED "Phase 74/75 baseline missing" "$details"; exit 1
  fi
  write_result 76A PREPARED "Forward-compatible baseline; Phase 74/75 not present in supplied ZIP"
else
  write_result 76A PASS "Phase 74/75 handoff detected"
fi
