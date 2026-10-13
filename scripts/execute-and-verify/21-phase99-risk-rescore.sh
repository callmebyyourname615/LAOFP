#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
bash -n scripts/phase99/99-runtime-evidence-risk-rescore.sh
test -f docs/phase99/PHASE99_RUNTIME_EVIDENCE_RISK_RESCORE.md
echo "Phase 99 risk re-score tooling is ready."
