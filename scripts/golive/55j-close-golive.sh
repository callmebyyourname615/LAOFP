#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55J; live_require_environment hypercare
require_phase_pass 55A 55B 55C 55D 55E 55F 55G 55H 55I
[[ "${BAU_HANDOVER_CONFIRMATION:-}" == I_UNDERSTAND_THIS_CLOSES_HYPERCARE_AND_RECORDS_OPERATIONAL_ACCEPTANCE ]] || live_die "BAU handover confirmation missing"
: "${BUSINESS_ACCEPTANCE_FILE:?required}"; : "${OPERATIONS_ACCEPTANCE_FILE:?required}"; : "${SECURITY_ACCEPTANCE_FILE:?required}"
: "${KNOWN_ISSUES_REGISTER_FILE:?required}"; : "${POST_IMPLEMENTATION_REVIEW_FILE:?required}"
: "${BUSINESS_ACCEPTANCE_SIGNATURE:?required}"; : "${OPERATIONS_ACCEPTANCE_SIGNATURE:?required}"; : "${SECURITY_ACCEPTANCE_SIGNATURE:?required}"; : "${ACCEPTANCE_PUBLIC_KEY:?required}"
for file in "$BUSINESS_ACCEPTANCE_FILE" "$OPERATIONS_ACCEPTANCE_FILE" "$SECURITY_ACCEPTANCE_FILE" "$KNOWN_ISSUES_REGISTER_FILE" "$POST_IMPLEMENTATION_REVIEW_FILE" "$BUSINESS_ACCEPTANCE_SIGNATURE" "$OPERATIONS_ACCEPTANCE_SIGNATURE" "$SECURITY_ACCEPTANCE_SIGNATURE"; do [[ -f "$file" && ! -L "$file" ]] || live_die "closure input must be a regular non-symlink file: $file"; done
live_require_command cosign
phase_begin 55J "Go-Live Closure and BAU Handover"
failed=0
cp "$KNOWN_ISSUES_REGISTER_FILE" "$PHASE_DIR/known-issues-register.json"
cp "$POST_IMPLEMENTATION_REVIEW_FILE" "$PHASE_DIR/post-implementation-review.md"
for domain in business operations security; do
  file_var="${domain^^}_ACCEPTANCE_FILE"; sig_var="${domain^^}_ACCEPTANCE_SIGNATURE"
  file="${!file_var}"; sig="${!sig_var}"
  cp "$file" "$PHASE_DIR/$domain-acceptance.json"; cp "$sig" "$PHASE_DIR/$domain-acceptance.sig"
  run_check "$domain-acceptance-signature" cosign verify-blob --key "$ACCEPTANCE_PUBLIC_KEY" --signature "$PHASE_DIR/$domain-acceptance.sig" "$PHASE_DIR/$domain-acceptance.json" || failed=1
done
run_check acceptance-contract python3 scripts/golive/verify_operational_acceptances.py --business "$PHASE_DIR/business-acceptance.json" --operations "$PHASE_DIR/operations-acceptance.json" --security "$PHASE_DIR/security-acceptance.json" --known-issues "$PHASE_DIR/known-issues-register.json" --hypercare-summary "$GOLIVE_ROOT/phases/55I/hypercare-summary.json" --post-implementation-review "$PHASE_DIR/post-implementation-review.md" --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" --output "$PHASE_DIR/operational-acceptance.json" || failed=1
run_check final-evidence-secret-scan python3 scripts/certification/scan_evidence.py --root "$GOLIVE_ROOT" --output "$PHASE_DIR/evidence-secret-scan.json" || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
write_phase_result PASS
mkdir -p "$GOLIVE_ROOT/operational-acceptance"
set +e
python3 scripts/golive/build_operational_acceptance.py --root "$GOLIVE_ROOT" --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" --application-digest "$RELEASE_APP_IMAGE_DIGEST" --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST" --output "$GOLIVE_ROOT/operational-acceptance/manifest.json"
rc=$?
set -e
if (( rc != 0 )); then write_phase_result FAIL; exit "$rc"; fi
