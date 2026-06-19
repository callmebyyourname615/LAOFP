#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this script inside the Switching Git repository after overlaying the changed-files ZIP." >&2
  exit 2
}
cd "$ROOT"

required=(
  src/main/resources/db/migration/V83__align_payload_sha256_to_varchar.sql
  src/main/java/com/example/switching/migration/MigrationApplication.java
  src/main/java/com/example/switching/observability/OperationalMetricsConfiguration.java
  src/test/java/com/example/switching/migration/MigrationApplicationIntegrationTest.java
  config/production-environment-contract.yaml
  config/runtime-evidence-plan.yaml
  scripts/verify_all_static.py
  scripts/verify_phases_53c_53j.py
  docs/templates/PRODUCTION_GO_LIVE_SIGNOFF.md
)
for path in "${required[@]}"; do
  [[ -f "$path" ]] || { echo "Phase 53C-53J apply check failed; missing $path" >&2; exit 1; }
done

chmod +x \
  scripts/verify_all_static.py \
  scripts/verify_phases_53c_53j.py \
  scripts/validate_production_environment.py \
  scripts/evidence/*.py scripts/evidence/*.sh \
  scripts/monitoring/*.py scripts/monitoring/*.sh \
  scripts/release/*.sh \
  apply-phases53c-53j.sh

python3 scripts/verify_all_static.py
python3 scripts/tests/test_validate_production_environment.py
python3 scripts/evidence/test_runtime_evidence.py

if [[ "${PHASE53CJ_RUN_MAVEN:-false}" == "true" ]]; then
  ./mvnw --batch-mode --no-transfer-progress \
    -Dtest=MigrationApplicationIntegrationTest,MigrationRuntimeIsolationContractTest,OperationalMetricsConfigurationTest \
    test
else
  echo "Phase 53C-53J overlay verified with repository-side static and regression gates."
  echo "Run PHASE53CJ_RUN_MAVEN=true ./apply-phases53c-53j.sh in a Docker-enabled environment."
fi

echo "Production remains NO-GO until the runtime evidence manifest passes --require-go-live-ready."
