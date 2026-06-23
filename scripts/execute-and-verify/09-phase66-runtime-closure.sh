#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase66_static.py
scripts/phase66/run_phase66.sh --preflight
