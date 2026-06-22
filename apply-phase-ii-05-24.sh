#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

printf '[phase-ii-05-24] verifying changed-file static contract\n'
python3 scripts/verify_phase_ii_01_04_static.py
python3 scripts/verify_phase_ii_05_24_static.py

if [[ "${PHASE_II_STRICT_PREDECESSORS:-false}" == "true" ]]; then
  python3 scripts/verify_phase_ii_01_04_static.py --strict-predecessors
fi

if [[ "${PHASE_II_RUN_STATIC_ALL:-false}" == "true" ]]; then
  python3 scripts/verify_all_static.py
fi

if [[ "${PHASE_II_RUN_MAVEN:-false}" == "true" ]]; then
  ./mvnw verify
fi

printf '[phase-ii-05-24] OK\n'
