#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase65_static.py
scripts/phase65/run_phase65.sh --preflight
