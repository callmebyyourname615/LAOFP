#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
python3 scripts/verify_phase77_static.py
RUN_ID="phase77-preflight-$(date -u +%Y%m%dT%H%M%SZ)" scripts/phase77/run_phase77.sh
