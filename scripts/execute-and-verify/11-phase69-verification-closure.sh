#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase69_static.py
PHASE69_MODE=preflight scripts/phase69/run_phase69.sh --preflight
