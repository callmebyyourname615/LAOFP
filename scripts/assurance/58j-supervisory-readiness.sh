#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58J 'Supervisory Readiness & Continuous Control Validation'; assurance_require_environment regulatory compliance
: "${SUPERVISORY_READINESS_INPUT:?SUPERVISORY_READINESS_INPUT is required}"; assurance_require_file "$SUPERVISORY_READINESS_INPUT"; assurance_require_phase_pass 58A 58B 58C 58D 58E 58F 58G 58H 58I
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "$SUPERVISORY_READINESS_INPUT" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check supervisory-certificate python3 scripts/assurance/issue_supervisory_certificate.py --assurance-root "$ASSURANCE_ROOT" --input "$SUPERVISORY_READINESS_INPUT" --controls supervisory-readiness/control-catalog.yaml --scoring supervisory-readiness/scoring-policy.yaml --policy supervisory-readiness/certification-policy.yaml --thresholds "$ASSURANCE_THRESHOLDS" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" --now "${ASSURANCE_NOW:-$(assurance_now)}" --score-output "$PHASE_DIR/readiness-score.json" --controls-output "$PHASE_DIR/control-results.json" --certificate-output "$PHASE_DIR/supervisory-certificate.json" || failed=1
assurance_run_check evidence-manifest python3 scripts/assurance/build_evidence_manifest.py --root "$ASSURANCE_ROOT/phases" --output "$PHASE_DIR/evidence-manifest.json" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check manifest-verify python3 scripts/assurance/verify_evidence_manifest.py --manifest "$PHASE_DIR/evidence-manifest.json" --root "$ASSURANCE_ROOT/phases" || failed=1
assurance_run_check manifest-signature scripts/assurance/sign_and_verify_blob.sh "$PHASE_DIR/evidence-manifest.json" "$PHASE_DIR/evidence-manifest.sig" "$PHASE_DIR/evidence-manifest-verification.txt" || failed=1
if [[ "${ASSURANCE_UPLOAD_EVIDENCE:-false}" == true ]]; then
  assurance_run_check immutable-evidence-upload scripts/assurance/evidence_store.sh "$ASSURANCE_ROOT" "$RELEASE_REFERENCE" || failed=1
fi
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
