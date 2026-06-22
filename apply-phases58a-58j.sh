#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
python3 scripts/verify_phase58_static.py
if [[ "${PHASE58_RUN_FRAMEWORK_TESTS:-false}" == true ]]; then python3 -m unittest scripts.assurance.tests.test_phase58_framework; fi
if [[ "${PHASE58_RUN_PRIOR_STATIC_GATES:-false}" == true ]]; then python3 scripts/verify_phase57_static.py; fi
if [[ "${PHASE58_RUN_REPOSITORY_HYGIENE:-false}" == true ]]; then scripts/security/scan_repository_secrets.sh --path . --mode manifest --manifest PHASES_58A_58J_FILE_MANIFEST.txt; fi
echo 'Phase 58A-58J repository implementation applied successfully.'
