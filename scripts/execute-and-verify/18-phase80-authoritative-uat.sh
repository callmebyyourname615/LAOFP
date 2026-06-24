#!/usr/bin/env bash
set -Eeuo pipefail
python3 scripts/verify_phase80_static.py
PHASE80_MODE=preflight scripts/phase80/run_phase80.sh preflight
