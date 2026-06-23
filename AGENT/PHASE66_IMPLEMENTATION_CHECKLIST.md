# Phase 66A–66J Implementation Checklist

> Scope: additive runtime-closure framework only. Do not modify Phase 65, application source, tests, Flyway migrations, or `pom.xml`.

## Boundary and foundation

- [x] 66A Define Phase 65 handoff discovery and changed-file collision guard.
- [x] 66A Record repository identity, dirty-state policy, migration head and protected paths.
- [x] Add Phase 66 common evidence/result utilities and guarded execution modes.

## Runtime closure phases

- [x] 66B Implement non-secret UAT dependency/connectivity preflight.
- [x] 66C Implement Maven/JUnit runtime closure without editing test source.
- [x] 66D Implement Flyway inventory, schema/data-integrity and replica-lag certification.
- [x] 66E Implement guarded k6/settlement campaign orchestration and result normalization.
- [x] 66F Implement guarded backup, verification, restore and RPO/RTO evidence collection.
- [x] 66G Implement guarded DR campaign orchestration and scenario-result validation.
- [x] 66H Implement secret-rotation ceremony control without recording secret values.
- [x] 66I Implement live SMOS security/provisioning checks without embedding credentials.
- [x] 66J Implement tamper-evident closure bundle and Phase 54 entry decision.

## Contracts and automation

- [x] Add Phase 66 YAML contracts and JSON schemas.
- [x] Add CI workflow for repository/preflight validation and guarded UAT dispatch.
- [x] Add execute-and-verify entry point.
- [x] Add operator reports/runbooks for 66A–66J.

## Validation

- [x] Validate every shell script with `bash -n`.
- [x] Validate every JSON schema/template with Python JSON parser.
- [x] Validate every YAML file with PyYAML when available.
- [x] Run Phase 66 static verifier.
- [x] Run Phase 66 preflight A–J and confirm decision is `PREPARED`, never `CERTIFIED`.
- [x] Verify changed files stay inside the Phase 66 allowlist.
- [x] Attempt Maven compile/test validation and record any environment blocker honestly.
- [x] Build changed-files-only ZIP and SHA-256 digest.
