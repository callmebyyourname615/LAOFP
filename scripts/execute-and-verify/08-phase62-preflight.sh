#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
export PHASE62_RUN_ID="${PHASE62_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE62_EVIDENCE_ROOT="${EVIDENCE_DIR:-$PWD/scripts/phase62/evidence}"
scripts/phase62/run_phase62.sh --preflight
