#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67G
p67_require_environment production
p67_require_production_confirmation
p67_begin 67G "Automated Rollback Decision Engine"
failed=0
if [[ "$PHASE67_MODE" == preflight ]]; then
  p67_run_check healthy-policy python3 scripts/phase67/phase67_control.py decision \
    --policy "$PHASE67_POLICY" --input docs/templates/phase67/HEALTHY_CUTOVER_SIGNALS.example.json --stage synthetic-healthy \
    --output "$PHASE_DIR/healthy-decision.json" --allowed-decision CONTINUE \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE" || failed=1
  p67_run_check rollback-policy bash -c '
    set +e
    python3 scripts/phase67/phase67_control.py decision --policy "$1" --input docs/templates/phase67/ROLLBACK_CUTOVER_SIGNALS.example.json --stage synthetic-rollback --output "$2" --allowed-decision CONTINUE --reference "$3" --rc-id "$4" --git-commit "$5" --application-digest "$6" --migration-digest "$7" --environment "$8" --mode "$9"
    rc=$?
    set -e
    test "$rc" -eq 2
    test "$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))[\"decision\"])" "$2")" = ROLLBACK_REQUIRED
    exit 3
  ' _ "$PHASE67_POLICY" "$PHASE_DIR/rollback-decision.json" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" "$RELEASE_APP_IMAGE_DIGEST" "$RELEASE_MIGRATION_IMAGE_DIGEST" "$PHASE67_ENVIRONMENT" "$PHASE67_MODE" || failed=1
else
  p67_require_phase67_pass 67A 67B 67C 67D 67E 67F
  signals="${PHASE67_ROLLBACK_SIGNALS_FILE:-$PHASE67_ROOT/phases/67F/latest-cutover-signals.json}"
  p67_run_check rollback-decision python3 scripts/phase67/phase67_control.py decision \
    --policy "$PHASE67_POLICY" --input "$signals" --stage production-100 \
    --output "$PHASE_DIR/rollback-decision.json" --allowed-decision CONTINUE \
    --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
    --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --environment "$PHASE67_ENVIRONMENT" --mode "$PHASE67_MODE" || failed=1
fi
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
