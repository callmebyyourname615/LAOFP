#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55A; live_require_environment release
: "${PHASE54_MANIFEST:?PHASE54_MANIFEST is required}"
: "${APPLICATION_IMAGE_REPOSITORY:?APPLICATION_IMAGE_REPOSITORY is required}"
: "${MIGRATION_IMAGE_REPOSITORY:?MIGRATION_IMAGE_REPOSITORY is required}"
: "${COSIGN_PUBLIC_KEY:?COSIGN_PUBLIC_KEY is required}"
: "${RELEASE_PACKAGE_SIGNING_KEY:?RELEASE_PACKAGE_SIGNING_KEY is required}"
: "${RELEASE_PACKAGE_PUBLIC_KEY:?RELEASE_PACKAGE_PUBLIC_KEY is required}"
live_require_image_repository "$APPLICATION_IMAGE_REPOSITORY" application-image-repository
live_require_image_repository "$MIGRATION_IMAGE_REPOSITORY" migration-image-repository
live_require_command cosign; live_require_command syft; live_require_command python3
: "${SOURCE_DATE_EPOCH:?SOURCE_DATE_EPOCH is required for deterministic release assembly}"
[[ "$SOURCE_DATE_EPOCH" =~ ^[0-9]{9,12}$ ]] || live_die "invalid SOURCE_DATE_EPOCH"
phase_begin 55A "Immutable Release Candidate Assembly"
failed=0
app_ref="${APPLICATION_IMAGE_REPOSITORY}@${RELEASE_APP_IMAGE_DIGEST}"
migration_ref="${MIGRATION_IMAGE_REPOSITORY}@${RELEASE_MIGRATION_IMAGE_DIGEST}"
work="$PHASE_DIR/work"; candidate="$PHASE_DIR/release-candidate"; mkdir -p "$work" "$candidate"

run_check verify-application-signature bash -c 'cosign verify --key "$1" "$2" > "$3"' _ "$COSIGN_PUBLIC_KEY" "$app_ref" "$work/application-verification.txt" || failed=1
run_check verify-migration-signature bash -c 'cosign verify --key "$1" "$2" > "$3"' _ "$COSIGN_PUBLIC_KEY" "$migration_ref" "$work/migration-verification.txt" || failed=1
run_check verify-application-provenance bash -c 'cosign verify-attestation --key "$1" --type slsaprovenance "$2" > "$3"' _ "$COSIGN_PUBLIC_KEY" "$app_ref" "$work/application.intoto.jsonl" || failed=1
run_check verify-migration-provenance bash -c 'cosign verify-attestation --key "$1" --type slsaprovenance "$2" > "$3"' _ "$COSIGN_PUBLIC_KEY" "$migration_ref" "$work/migration.intoto.jsonl" || failed=1
run_check application-sbom syft "$app_ref" -o "spdx-json=$work/application.spdx.json" || failed=1
run_check migration-sbom syft "$migration_ref" -o "spdx-json=$work/migration.spdx.json" || failed=1
run_check render-application-manifest scripts/render_k8s_image.sh k8s/deployment.yaml "$work/deployment.yaml" "$APPLICATION_IMAGE_REPOSITORY" "$RELEASE_APP_IMAGE_DIGEST" || failed=1
run_check render-migration-manifest scripts/render_k8s_image.sh k8s/migration-job.yaml "$work/migration-job.yaml" "$MIGRATION_IMAGE_REPOSITORY" "$RELEASE_MIGRATION_IMAGE_DIGEST" || failed=1
run_check manifest-immutability bash -c '! grep -R -E ":latest|REPLACE_WITH_IMAGE_DIGEST|REPLACE_WITH_GITHUB_REPOSITORY" "$1" "$2"' _ "$work/deployment.yaml" "$work/migration-job.yaml" || failed=1

if (( failed == 0 )); then
  run_check assemble-candidate python3 scripts/golive/assemble_release_candidate.py \
    --output "$candidate" --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" \
    --application-image-repository "$APPLICATION_IMAGE_REPOSITORY" --application-image-digest "$RELEASE_APP_IMAGE_DIGEST" \
    --migration-image-repository "$MIGRATION_IMAGE_REPOSITORY" --migration-image-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --phase54-manifest "$PHASE54_MANIFEST" --source-date-epoch "$SOURCE_DATE_EPOCH" \
    --artifact "$work/application.spdx.json=sbom/application.spdx.json" \
    --artifact "$work/migration.spdx.json=sbom/migration.spdx.json" \
    --artifact "$work/application-verification.txt=signatures/application-verification.txt" \
    --artifact "$work/migration-verification.txt=signatures/migration-verification.txt" \
    --artifact "$work/application.intoto.jsonl=provenance/application.intoto.jsonl" \
    --artifact "$work/migration.intoto.jsonl=provenance/migration.intoto.jsonl" \
    --artifact "$work/deployment.yaml=manifests/deployment.yaml" \
    --artifact "$work/migration-job.yaml=manifests/migration-job.yaml" || failed=1
fi
if (( failed == 0 )); then
  run_check verify-candidate python3 scripts/golive/verify_release_candidate.py --root "$candidate" \
    --expected-rc-id "$RELEASE_RC_ID" --expected-git-commit "$RELEASE_GIT_COMMIT" \
    --expected-application-digest "$RELEASE_APP_IMAGE_DIGEST" --expected-migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" \
    --output "$PHASE_DIR/release-candidate-verification.json" || failed=1
fi
if (( failed == 0 )); then
  source_epoch="$SOURCE_DATE_EPOCH"
  run_check package-candidate python3 scripts/golive/package_release_candidate.py --root "$candidate" --output "$PHASE_DIR/release-candidate.tar.gz" --mtime "$source_epoch" --checksum-output "$PHASE_DIR/release-candidate.tar.gz.sha256" || failed=1
  run_check sign-release-package cosign sign-blob --yes --key "$RELEASE_PACKAGE_SIGNING_KEY" --output-signature "$PHASE_DIR/release-candidate.tar.gz.sig" "$PHASE_DIR/release-candidate.tar.gz" || failed=1
  run_check verify-release-package-signature bash -c 'cosign verify-blob --key "$1" --signature "$2" "$3" > "$4"' _ "$RELEASE_PACKAGE_PUBLIC_KEY" "$PHASE_DIR/release-candidate.tar.gz.sig" "$PHASE_DIR/release-candidate.tar.gz" "$PHASE_DIR/release-package-signature-verification.txt" || failed=1
fi
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
