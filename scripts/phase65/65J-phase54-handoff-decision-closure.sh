#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65J 'Phase 54 handoff and decision closure'
for f in docs/templates/PHASE65_DECISIONS.example.json docs/templates/PHASE65_PHASE54_HANDOFF.example.json scripts/phase65/build_phase54_handoff.py; do [[ -f "$f" ]] || exit 1; done
if phase_preflight; then phase_finalize PREPARED 0 'decision register and signed Phase 54 handoff builder ready'; exit 0; fi
: "${PHASE65_DECISIONS:?decisions JSON required}"; : "${PHASE65_PHASE54_HANDOFF:?handoff JSON required}"
phase_run 'build and verify Phase 54 handoff' python3 scripts/phase65/build_phase54_handoff.py --decisions "$PHASE65_DECISIONS" --handoff "$PHASE65_PHASE54_HANDOFF" --output "$PHASE65_DIR/phase54-handoff-bundle.json"
phase_finalize PASS 0 'open decisions closed and Phase 54 entry handoff approved'
