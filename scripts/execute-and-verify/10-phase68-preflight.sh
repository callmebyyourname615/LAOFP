#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase68_static.py
PHASE68_RUN_ID="${PHASE68_RUN_ID:-preflight-$(date -u +%Y%m%dT%H%M%SZ)}" scripts/phase68/run_phase68.sh --preflight
