#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56J "Continuous Resilience Certification"; day2_require_environment dr; day2_require_phase_pass 56A 56B 56C 56D 56E 56F 56G 56H 56I
: "${RESILIENCE_RESULTS:?RESILIENCE_RESULTS is required}"; cp "$RESILIENCE_RESULTS" "$PHASE_DIR/resilience-results.json"
day2_run_check certificate python3 scripts/day2/issue_resilience_certificate.py --results "$PHASE_DIR/resilience-results.json" --policy resilience/certification-policy.yaml --thresholds resilience/acceptance-thresholds.yaml --catalog resilience/scenario-catalog.yaml --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" --output "$PHASE_DIR/resilience-certificate.json" || true
day2_write_checksum "$PHASE_DIR/resilience-certificate.json" "$PHASE_DIR/resilience-certificate.sha256"
: "${RESILIENCE_SIGNING_KEY:?RESILIENCE_SIGNING_KEY is required}"; : "${RESILIENCE_PUBLIC_KEY:?RESILIENCE_PUBLIC_KEY is required}"; day2_require_command cosign
day2_run_check sign-certificate cosign sign-blob --yes --key "$RESILIENCE_SIGNING_KEY" --output-signature "$PHASE_DIR/resilience-certificate.sig" "$PHASE_DIR/resilience-certificate.json" || true
day2_run_check verify-certificate cosign verify-blob --key "$RESILIENCE_PUBLIC_KEY" --signature "$PHASE_DIR/resilience-certificate.sig" "$PHASE_DIR/resilience-certificate.json" || true
day2_write_result
