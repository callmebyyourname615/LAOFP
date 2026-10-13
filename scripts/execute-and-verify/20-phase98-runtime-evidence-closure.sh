#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
bash -n scripts/phase98/98-production-runtime-evidence-closure.sh
test -f docs/phase98/PHASE98_PRODUCTION_RUNTIME_EVIDENCE_CLOSURE.md
echo "Phase 98 runtime evidence closure tooling is ready."
