#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56F "Continuous Compliance and Audit Evidence"; day2_require_environment production compliance
day2_run_check collect python3 scripts/day2/collect_compliance_evidence.py --mapping compliance/evidence-mapping.yaml --controls compliance/phase56-controls.yaml --root . --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" --output "$PHASE_DIR/evidence-manifest.json" || true
day2_write_checksum "$PHASE_DIR/evidence-manifest.json" "$PHASE_DIR/evidence-manifest.sha256"
: "${EVIDENCE_SIGNING_KEY:?EVIDENCE_SIGNING_KEY is required}"; : "${EVIDENCE_PUBLIC_KEY:?EVIDENCE_PUBLIC_KEY is required}"; day2_require_command cosign
day2_run_check sign-manifest cosign sign-blob --yes --key "$EVIDENCE_SIGNING_KEY" --output-signature "$PHASE_DIR/evidence-manifest.sig" "$PHASE_DIR/evidence-manifest.json" || true
day2_run_check verify-signature cosign verify-blob --key "$EVIDENCE_PUBLIC_KEY" --signature "$PHASE_DIR/evidence-manifest.sig" "$PHASE_DIR/evidence-manifest.json" || true
day2_run_check verify python3 scripts/day2/verify_evidence_chain.py --manifest "$PHASE_DIR/evidence-manifest.json" --root . || true
cp "$PHASE_DIR/evidence-manifest.json" "$PHASE_DIR/control-report.json"; day2_write_result
