# Phase 72 Changed Files

This package is additive and intentionally excludes Phase 71-owned files, Flyway migrations, `pom.xml`, and unrelated baseline changes.

## Execution and verification

- `.github/workflows/phase72-final-uat-closure.yml`
- `scripts/execute-and-verify/13-phase72-final-uat-closure.sh`
- `scripts/phase72/72A-phase71-handoff-collision-guard.sh`
- `scripts/phase72/72B-crossborder-temporal-binding-closure.sh`
- `scripts/phase72/72C-full-maven-verification-closure.sh`
- `scripts/phase72/72D-repository-verification-gate.sh`
- `scripts/phase72/72E-uat-environment-activation.sh`
- `scripts/phase72/72F-performance-evidence-campaign.sh`
- `scripts/phase72/72G-backup-pitr-dr-certification.sh`
- `scripts/phase72/72H-secret-rotation-purge-ceremony.sh`
- `scripts/phase72/72I-runtime-security-alert-certification.sh`
- `scripts/phase72/72J-build-phase54-go-no-go-bundle.sh`
- `scripts/phase72/build_phase72_manifest.py`
- `scripts/phase72/collect_junit_results.py`
- `scripts/phase72/common.sh`
- `scripts/phase72/normalize_performance_results.py`
- `scripts/phase72/run_phase72.sh`
- `scripts/phase72/validate_attestation.py`
- `scripts/phase72/validate_resilience_summary.py`
- `scripts/phase72/write_phase_result.py`
- `scripts/verify_cross_border_temporal_binding.py`
- `scripts/verify_phase72_static.py`

## Policies and schemas

- `config/phase72/file-ownership-policy.yaml`
- `config/phase72/final-uat-policy.yaml`
- `config/phase72/performance-thresholds.yaml`
- `config/phase72/resilience-policy.yaml`
- `config/phase72/uat-dependencies.yaml`
- `schemas/phase72/dependency-result.schema.json`
- `schemas/phase72/final-go-attestation.schema.json`
- `schemas/phase72/performance-summary.schema.json`
- `schemas/phase72/phase72-manifest.schema.json`
- `schemas/phase72/phase72-result.schema.json`
- `schemas/phase72/runtime-security-attestation.schema.json`
- `schemas/phase72/secret-rotation-attestation.schema.json`
- `sql/phase72/post-restore-financial-integrity.sql`

## Documentation and checklist

- `AGENT/PHASE72A-72J_CHECKLIST.md`
- `docs/phase72/CROSS_BORDER_TIMESTAMP_FIX_REPORT.md`
- `docs/phase72/PHASE72_EXIT_CRITERIA.md`
- `docs/phase72/PHASE72_IMPLEMENTATION.md`
- `docs/phase72/PHASE72_OPERATOR_RUNBOOK.md`
- `docs/phase72/VALIDATION_REPORT.md`
- `docs/templates/PHASE72_FINAL_GO_ATTESTATION.example.json`
- `docs/templates/PHASE72_RUNTIME_SECURITY_ATTESTATION.example.json`
- `docs/templates/PHASE72_SECRET_ROTATION_ATTESTATION.example.json`

## Regression test

- `src/test/java/com/example/switching/crossborder/CrossBorderTemporalBindingRegressionTest.java`

## Validation evidence

- `evidence/phase72/validation-full-guard/artifacts/environment-fingerprint.txt`
- `evidence/phase72/validation-full-guard/results/72A.json`
- `evidence/phase72/validation-local/logs/final-static-validation.log`
- `evidence/phase72/validation-local/logs/javac-regression-test.log`
- `evidence/phase72/validation-local/logs/manifest-decision-policy-test.log`
- `evidence/phase72/validation-local/logs/maven-temporal-test-attempt.log`
- `evidence/phase72/validation-local/logs/performance-threshold-policy-test.log`
- `evidence/phase72/validation-preflight-final/SHA256SUMS`
- `evidence/phase72/validation-preflight-final/artifacts/cross-border-temporal-binding.json`
- `evidence/phase72/validation-preflight-final/artifacts/environment-fingerprint.txt`
- `evidence/phase72/validation-preflight-final/logs/72B-scan.log`
- `evidence/phase72/validation-preflight-final/logs/72B-self-test.log`
- `evidence/phase72/validation-preflight-final/logs/72D-static.log`
- `evidence/phase72/validation-preflight-final/phase72-final-uat-manifest.json`
- `evidence/phase72/validation-preflight-final/results/72A.json`
- `evidence/phase72/validation-preflight-final/results/72B.json`
- `evidence/phase72/validation-preflight-final/results/72C.json`
- `evidence/phase72/validation-preflight-final/results/72D.json`
- `evidence/phase72/validation-preflight-final/results/72E.json`
- `evidence/phase72/validation-preflight-final/results/72F.json`
- `evidence/phase72/validation-preflight-final/results/72G.json`
- `evidence/phase72/validation-preflight-final/results/72H.json`
- `evidence/phase72/validation-preflight-final/results/72I.json`
- `evidence/phase72/validation-preflight-final/results/72J.json`

## Runtime status

Implementation preflight is `PREPARED`. Real UAT execution and signatures remain pending.
