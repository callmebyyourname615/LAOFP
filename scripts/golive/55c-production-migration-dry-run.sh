#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55C; live_require_environment production-dry-run
require_phase_pass 55A 55B
[[ "${MIGRATION_DRY_RUN_CONFIRMATION:-}" == I_UNDERSTAND_THIS_RESTORES_AN_ISOLATED_PRODUCTION_LIKE_DATABASE ]] || live_die "migration dry-run confirmation missing"
: "${DRY_RUN_RESTORE_SCRIPT:?DRY_RUN_RESTORE_SCRIPT is required}"; [[ -x "$DRY_RUN_RESTORE_SCRIPT" ]] || live_die "restore script is not executable"
: "${DRY_RUN_DB_URL:?DRY_RUN_DB_URL is required}"; : "${DRY_RUN_DB_USERNAME:?DRY_RUN_DB_USERNAME is required}"; : "${DRY_RUN_DB_PASSWORD:?DRY_RUN_DB_PASSWORD is required}"
: "${MIGRATION_IMAGE_REPOSITORY:?MIGRATION_IMAGE_REPOSITORY is required}"; : "${APPLICATION_IMAGE_REPOSITORY:?APPLICATION_IMAGE_REPOSITORY is required}"
: "${DRY_RUN_APP_ENV_FILE:?DRY_RUN_APP_ENV_FILE is required}"
: "${PREVIOUS_APPLICATION_IMAGE_REFERENCE:?PREVIOUS_APPLICATION_IMAGE_REFERENCE is required}"
[[ "$PREVIOUS_APPLICATION_IMAGE_REFERENCE" =~ @sha256:[a-f0-9]{64}$ ]] || live_die "previous application image must be digest-pinned"
: "${DRY_RUN_ROLLBACK_SCRIPT:?DRY_RUN_ROLLBACK_SCRIPT is required}"; [[ -x "$DRY_RUN_ROLLBACK_SCRIPT" ]] || live_die "rollback script is not executable"
: "${DRY_RUN_SNAPSHOT_ATTESTATION_FILE:?DRY_RUN_SNAPSHOT_ATTESTATION_FILE is required}"; [[ -f "$DRY_RUN_SNAPSHOT_ATTESTATION_FILE" && ! -L "$DRY_RUN_SNAPSHOT_ATTESTATION_FILE" ]] || live_die "snapshot attestation must be a regular non-symlink file"
: "${DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE:?DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE is required}"; [[ -f "$DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE" && ! -L "$DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE" ]] || live_die "snapshot attestation signature must be a regular non-symlink file"
: "${ATTESTATION_PUBLIC_KEY:?ATTESTATION_PUBLIC_KEY is required}"
DRY_RUN_EXPECTED_START_VERSION="${DRY_RUN_EXPECTED_START_VERSION:-82}"; [[ "$DRY_RUN_EXPECTED_START_VERSION" =~ ^[1-9][0-9]*$ ]] || live_die "invalid DRY_RUN_EXPECTED_START_VERSION"
[[ "$DRY_RUN_DB_URL" != "${DB_URL:-}" ]] || live_die "dry-run database must not equal production DB_URL"
live_require_image_repository "$APPLICATION_IMAGE_REPOSITORY" application-image-repository
live_require_image_repository "$MIGRATION_IMAGE_REPOSITORY" migration-image-repository
live_require_command psql; live_require_command docker; live_require_command openssl; live_require_command cosign
phase_begin 55C "Production Migration Dry Run"
failed=0
export DB_URL="$DRY_RUN_DB_URL" DB_USERNAME="$DRY_RUN_DB_USERNAME" DB_PASSWORD="$DRY_RUN_DB_PASSWORD"
run_check snapshot-attestation-signature cosign verify-blob --key "$ATTESTATION_PUBLIC_KEY" --signature "$DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE" "$DRY_RUN_SNAPSHOT_ATTESTATION_FILE" || failed=1
run_check snapshot-attestation python3 - "$DRY_RUN_SNAPSHOT_ATTESTATION_FILE" "$PHASE_DIR/snapshot-attestation.json" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" <<'PYATT' || failed=1
import datetime as dt,json,pathlib,re,sys
source,out,reference,rc_id,commit=sys.argv[1:]; doc=json.loads(pathlib.Path(source).read_text()); errors=[]
if doc.get('releaseReference')!=reference or doc.get('releaseCandidateId')!=rc_id or doc.get('gitCommit')!=commit: errors.append('snapshot attestation release identity mismatch')
if doc.get('anonymized') is not True: errors.append('snapshot must be anonymized')
if len({x for x in doc.get('approvedBy',[]) if x})<2: errors.append('two approvers required')
if not re.fullmatch(r'[a-f0-9]{64}',str(doc.get('sourceSnapshotIdHash',''))): errors.append('source snapshot SHA-256 required')
try:
    captured=dt.datetime.fromisoformat(str(doc.get('capturedAt','')).replace('Z','+00:00')); age=(dt.datetime.now(dt.timezone.utc)-captured).total_seconds()
    if age < 0 or age > 7*86400: errors.append('snapshot attestation must be within 7 days')
except Exception: errors.append('capturedAt invalid')
if errors: raise SystemExit('; '.join(errors))
pathlib.Path(out).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
PYATT
cp "$DRY_RUN_SNAPSHOT_ATTESTATION_SIGNATURE" "$PHASE_DIR/snapshot-attestation.sig"
if (( failed )); then write_phase_result FAIL; exit 1; fi
run_check restore-production-like-snapshot "$DRY_RUN_RESTORE_SCRIPT" || failed=1
run_check flyway-before bash -c 'export PGPASSWORD="$DB_PASSWORD"; psql "${DB_URL#jdbc:}" -X -v ON_ERROR_STOP=1 -At -F "|" -c "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank" > "$1"; tail -1 "$1" | grep -q "^$2|t$"' _ "$PHASE_DIR/flyway-before.txt" "$DRY_RUN_EXPECTED_START_VERSION" || failed=1
run_check capture-before python3 scripts/golive/capture_reconciliation.py --output "$PHASE_DIR/before.json" --label before-v83-dry-run --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || failed=1
if (( failed )); then
  run_check restore-after-preflight-failure "$DRY_RUN_ROLLBACK_SCRIPT" || true
  write_phase_result FAIL; exit 1
fi
start_epoch=$(date +%s)
dry_run_key=$(openssl rand -base64 32 | tr -d '\n')
run_check migrate-to-v83 docker run --rm --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m --entrypoint java \
  -e SPRING_PROFILES_ACTIVE=migration -e FLYWAY_URL="$DRY_RUN_DB_URL" -e FLYWAY_USERNAME="$DRY_RUN_DB_USERNAME" -e FLYWAY_PASSWORD="$DRY_RUN_DB_PASSWORD" \
  -e WEBHOOK_ENCRYPTION_PROVIDER=local -e WEBHOOK_LOCAL_MASTER_KEY_BASE64="$dry_run_key" \
  "${MIGRATION_IMAGE_REPOSITORY}@${RELEASE_MIGRATION_IMAGE_DIGEST}" -XX:+UseContainerSupport -Dloader.main=com.example.switching.migration.MigrationApplication -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher || failed=1
run_check migration-idempotent docker run --rm --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m --entrypoint java \
  -e SPRING_PROFILES_ACTIVE=migration -e FLYWAY_URL="$DRY_RUN_DB_URL" -e FLYWAY_USERNAME="$DRY_RUN_DB_USERNAME" -e FLYWAY_PASSWORD="$DRY_RUN_DB_PASSWORD" \
  -e WEBHOOK_ENCRYPTION_PROVIDER=local -e WEBHOOK_LOCAL_MASTER_KEY_BASE64="$dry_run_key" \
  "${MIGRATION_IMAGE_REPOSITORY}@${RELEASE_MIGRATION_IMAGE_DIGEST}" -XX:+UseContainerSupport -Dloader.main=com.example.switching.migration.MigrationApplication -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher || failed=1
end_epoch=$(date +%s)
duration_seconds=$((end_epoch-start_epoch)); max_duration="${MIGRATION_MAX_DURATION_SECONDS:-900}"
[[ "$max_duration" =~ ^[1-9][0-9]*$ ]] || live_die "invalid MIGRATION_MAX_DURATION_SECONDS"
if (( duration_seconds > max_duration )); then echo "migration exceeded maximum duration" >&2; failed=1; fi
run_check flyway-after bash -c 'export PGPASSWORD="$DB_PASSWORD"; psql "${DB_URL#jdbc:}" -X -v ON_ERROR_STOP=1 -At -F "|" -c "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank" > "$1"; tail -1 "$1" | grep -q "^83|t$"' _ "$PHASE_DIR/flyway-after.txt" || failed=1
run_check capture-after python3 scripts/golive/capture_reconciliation.py --output "$PHASE_DIR/after.json" --label after-v83-dry-run --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || failed=1
run_check data-reconciliation python3 scripts/golive/compare_reconciliation.py --baseline "$PHASE_DIR/before.json" --current "$PHASE_DIR/after.json" --output "$PHASE_DIR/data-reconciliation.json" || failed=1
run_check previous-application-v83-compatibility env IMAGE_REFERENCE="$PREVIOUS_APPLICATION_IMAGE_REFERENCE" EXPECTED_DB_URL="$DRY_RUN_DB_URL" DRY_RUN_APP_ENV_FILE="$DRY_RUN_APP_ENV_FILE" scripts/golive/verify_dry_run_application.sh || failed=1
run_check candidate-application-schema-startup env IMAGE_REFERENCE="${APPLICATION_IMAGE_REPOSITORY}@${RELEASE_APP_IMAGE_DIGEST}" EXPECTED_DB_URL="$DRY_RUN_DB_URL" DRY_RUN_APP_ENV_FILE="$DRY_RUN_APP_ENV_FILE" scripts/golive/verify_dry_run_application.sh || failed=1
run_check restore-rollback "$DRY_RUN_ROLLBACK_SCRIPT" || failed=1
run_check rollback-version bash -c 'export PGPASSWORD="$DB_PASSWORD"; actual=$(psql "${DB_URL#jdbc:}" -X -v ON_ERROR_STOP=1 -At -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"); test "$actual" = "$1"' _ "$DRY_RUN_EXPECTED_START_VERSION" || failed=1
python3 - "$PHASE_DIR/rollback-state.json" "$DRY_RUN_EXPECTED_START_VERSION" "$failed" <<'PYROLL'
import json,pathlib,sys
out,version,failed=sys.argv[1:]
pathlib.Path(out).write_text(json.dumps({'schemaVersion':1,'restoredFlywayVersion':version,'status':'PASS' if failed=='0' else 'FAIL'},indent=2,sort_keys=True)+'\n')
PYROLL
python3 - "$PHASE_DIR/migration-dry-run.json" "$start_epoch" "$end_epoch" "$failed" "$DRY_RUN_EXPECTED_START_VERSION" <<'PY'
import json,pathlib,sys
out,start,end,failed,from_version=sys.argv[1:]
doc={'schemaVersion':1,'fromVersion':from_version,'toVersion':'83','durationSeconds':int(end)-int(start),'schemaValidation':failed=='0','dataReconciliation':failed=='0','status':'PASS' if failed=='0' else 'FAIL'}
pathlib.Path(out).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
