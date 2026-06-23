#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase63_static.py
scripts/phase63/run_phase63.sh --preflight
