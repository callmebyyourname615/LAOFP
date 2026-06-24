#!/usr/bin/env bash
set -Eeuo pipefail
python3 scripts/verify_phase81_static.py
PHASE81_MODE=preflight scripts/phase81/run_phase81.sh preflight
