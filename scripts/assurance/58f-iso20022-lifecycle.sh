#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58F 'ISO 20022 & Market Practice Lifecycle'; assurance_require_environment operations regulatory
: "${ISO20022_LIFECYCLE_SNAPSHOT:?ISO20022_LIFECYCLE_SNAPSHOT is required}"; assurance_require_file "${ISO20022_LIFECYCLE_SNAPSHOT}"
assurance_require_phase_pass 58B
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${ISO20022_LIFECYCLE_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_iso20022_lifecycle.py --snapshot "${ISO20022_LIFECYCLE_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --lifecycle-policy iso20022/market-practice-lifecycle.yaml --change-policy iso20022/change-governance.yaml --code-set-policy iso20022/code-set-policy.yaml --report-output "$PHASE_DIR/iso20022-lifecycle.json" --decision-output "$PHASE_DIR/compatibility-decision.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
