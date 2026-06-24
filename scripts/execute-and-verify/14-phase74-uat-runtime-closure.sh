#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase74_75_static.py
scripts/phase74/run_phase74.sh --preflight
