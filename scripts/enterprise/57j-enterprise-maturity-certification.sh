#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57J "Enterprise Production Maturity Certification"; enterprise_require_environment compliance; enterprise_require_phase_pass 57A 57B 57C 57D 57E 57F 57G 57H 57I
: "${ENTERPRISE_MATURITY_INPUT:?ENTERPRISE_MATURITY_INPUT is required}"
: "${ENTERPRISE_CERTIFICATE_SIGNING_KEY:?ENTERPRISE_CERTIFICATE_SIGNING_KEY is required}"
: "${ENTERPRISE_CERTIFICATE_PUBLIC_KEY:?ENTERPRISE_CERTIFICATE_PUBLIC_KEY is required}"
enterprise_require_command cosign
enterprise_copy_input "$ENTERPRISE_MATURITY_INPUT" "$PHASE_DIR/maturity-input.json"
failed=0
enterprise_run_check maturity-evaluation python3 scripts/enterprise/issue_enterprise_maturity_certificate.py --enterprise-root "$ENTERPRISE_ROOT" --input "$PHASE_DIR/maturity-input.json" --model enterprise-certification/maturity-model.yaml --controls enterprise-certification/control-catalog.yaml --policy enterprise-certification/certification-policy.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" --score-output "$PHASE_DIR/maturity-score.json" --controls-output "$PHASE_DIR/control-results.json" --certificate-output "$PHASE_DIR/enterprise-certificate.json" || failed=1
if [[ $failed -eq 0 ]]; then
  enterprise_run_check certificate-attestation scripts/enterprise/sign_and_verify_blob.sh "$PHASE_DIR/enterprise-certificate.json" "$PHASE_DIR/enterprise-certificate.sig" "$ENTERPRISE_CERTIFICATE_SIGNING_KEY" "$ENTERPRISE_CERTIFICATE_PUBLIC_KEY" || failed=1
fi
if [[ $failed -eq 0 ]]; then
  enterprise_run_check evidence-package scripts/enterprise/build_sign_verify_manifest.sh "$ENTERPRISE_ROOT" "$PHASE_DIR" "$RELEASE_REFERENCE" "$RELEASE_GIT_COMMIT" "$RELEASE_IMAGE_DIGEST" "$ENTERPRISE_CERTIFICATE_SIGNING_KEY" "$ENTERPRISE_CERTIFICATE_PUBLIC_KEY" || failed=1
fi
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
