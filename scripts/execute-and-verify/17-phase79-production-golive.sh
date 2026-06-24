#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase78_79_static.py
scripts/phase79/run_phase79.sh --preflight
