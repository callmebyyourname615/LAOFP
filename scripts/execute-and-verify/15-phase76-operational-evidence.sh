#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
python3 scripts/verify_phase76_static.py
RUN_ID="phase76-preflight-$(date -u +%Y%m%dT%H%M%SZ)" scripts/phase76/run_phase76.sh --preflight
