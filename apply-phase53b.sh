#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

required=(
  pom.xml
  src/main/resources/db/migration/V82__controlled_decommissioning_data_exit.sql
  src/main/resources/db/migration/V83__align_payload_sha256_to_varchar.sql
  scripts/verify_phase53b_schema_alignment.py
)
for path in "${required[@]}"; do
  [[ -f "${path}" ]] || { echo "Phase 53B apply check failed; missing ${path}" >&2; exit 1; }
done

python3 scripts/verify_phase53b_schema_alignment.py

if [[ "${PHASE53B_RUN_MAVEN:-false}" == "true" ]]; then
  scripts/run_phase53b_verification.sh targeted
else
  echo "Phase 53B overlay verified statically."
  echo "Run PHASE53B_RUN_MAVEN=true ./apply-phase53b.sh or ./scripts/run_phase53b_verification.sh full in a Docker-enabled environment."
fi
