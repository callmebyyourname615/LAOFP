#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69A
cd "$PHASE69_ROOT"
python3 scripts/verify_phase69_static.py --boundary-only | tee "$PHASE69_LOG_DIR/$phase-boundary.log"
phase68_present=false
if [[ -d scripts/phase68 || -d docs/phase68 || -d config/phase68 || -d schemas/phase68 ]]; then phase68_present=true; fi
if [[ "$PHASE69_MODE" == full && "$phase68_present" != true ]]; then
  phase69_result "$phase" BLOCKED "Phase 68 is absent from the supplied baseline; merge Phase 68 before full verification" \
    --evidence "logs/$phase-boundary.log" --metric phase68Present=false
  exit 2
fi
status=PASS; message="Phase 68 ownership boundary is clean"
if [[ "$phase68_present" != true ]]; then status=PREPARED; message="Ownership boundary is clean; Phase 68 presence will be required for full mode"; fi
phase69_result "$phase" "$status" "$message" --evidence "logs/$phase-boundary.log" \
  --metric phase68Present="$phase68_present" --metric baselineCommit="$(phase69_git_sha)"
