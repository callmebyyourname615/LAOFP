#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase71_static.py
scripts/phase71/run_phase71.sh --preflight
