#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

required=(
  pom.xml
  src/main/resources/application.yml
  src/main/resources/db/migration/V91__request_to_pay_foundation.sql
  scripts/verify_phase_ii_01_04_static.py
  PHASE_II_01_04_FILE_MANIFEST.txt
)

for path in "${required[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "FAIL: expected project file is missing: $path" >&2
    exit 1
  fi
done

chmod +x \
  scripts/verify_phase_ii_01_04_static.py \
  scripts/verify_all_static.py \
  scripts/curl_rtp_tests.sh

python3 scripts/verify_phase_ii_01_04_static.py

if [[ "${PHASE_II_STRICT_PREDECESSORS:-false}" == "true" ]]; then
  python3 scripts/verify_phase_ii_01_04_static.py --strict-predecessors
fi

if [[ "${PHASE_II_RUN_MAVEN:-false}" == "true" ]]; then
  ./mvnw -B -Dtest=RtpStateMachineTest,RtpRequestFingerprintTest,RequestToPayControllerTest,RtpRequestIntegrationTest test
fi

echo "Phase II-01 through II-04 files applied and repository-side checks completed."
