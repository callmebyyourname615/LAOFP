#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/assurance/common.sh
assurance_require_identity; assurance_phase_begin 58C 'Cryptographic Agility & Post-Quantum Readiness'; assurance_require_environment security compliance
: "${CRYPTO_AGILITY_SNAPSHOT:?CRYPTO_AGILITY_SNAPSHOT is required}"; assurance_require_file "${CRYPTO_AGILITY_SNAPSHOT}"
assurance_require_phase_pass 58A
failed=0
assurance_run_check input-identity python3 scripts/assurance/verify_input_identity.py --input "${CRYPTO_AGILITY_SNAPSHOT}" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" || failed=1
assurance_run_check domain-evaluation python3 scripts/assurance/evaluate_crypto_agility.py --snapshot "${CRYPTO_AGILITY_SNAPSHOT}" --thresholds "$ASSURANCE_THRESHOLDS" --now "${ASSURANCE_NOW:-$(assurance_now)}" --algorithm-policy crypto-agility/algorithm-policy.yaml --pqc-policy crypto-agility/pqc-readiness-policy.yaml --key-policy crypto-agility/key-lifecycle-policy.yaml --report-output "$PHASE_DIR/crypto-agility.json" --plan-output "$PHASE_DIR/migration-plan.json" || failed=1
assurance_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
