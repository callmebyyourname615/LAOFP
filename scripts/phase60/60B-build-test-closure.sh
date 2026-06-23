#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60B" "Build and full test closure"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="build or verification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_require_command java
phase_require_command python3
phase_require_file ./mvnw
java -version 2> "$PHASE60_PHASE_DIR/java-version.txt"

if phase_is_preflight; then
  # Preflight must be deterministic and offline. Full historical static gates and
  # Maven dependency resolution run in --repo/--full mode.
  phase_run "Phase 60 static contract" python3 scripts/verify_phase60_static.py
  phase_run "SMOS static contract" python3 scripts/verify_smos_user_management_static.py
  phase_run "critical dashboard static contract" python3 scripts/verify_critical_dashboards_static.py
  phase_run "production environment template contract" python3 scripts/validate_production_environment.py \
    --env-file .env.prod.example --template
  python3 scripts/phase60/summarize_test_reports.py --root . \
    --output "$PHASE60_PHASE_DIR/test-summary.json" --allow-empty --allow-stale || true
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="offline build/test contracts are prepared; Maven verify and the full historical static suite were not executed"
  exit 0
fi

./mvnw --version > "$PHASE60_PHASE_DIR/maven-version.txt" 2>&1 || {
  phase_log "Maven wrapper could not start"
  exit 1
}
phase_run "repository static contracts" python3 scripts/verify_all_static.py
phase_run "Phase 60 static contract" python3 scripts/verify_phase60_static.py

set +e
./mvnw -B clean verify 2>&1 | tee "$PHASE60_PHASE_DIR/maven-verify.log"
maven_code=${PIPESTATUS[0]}
set -e
python3 scripts/phase60/summarize_test_reports.py --root . \
  --output "$PHASE60_PHASE_DIR/test-summary.json"
summary_code=$?
if [[ "$maven_code" -ne 0 || "$summary_code" -ne 0 ]]; then
  PHASE_MESSAGE="mvn verify did not close with zero failures and zero errors"
  exit 1
fi

PHASE_STATUS="PASS"
PHASE_MESSAGE="mvn clean verify and all static contracts passed with zero test failures/errors"
