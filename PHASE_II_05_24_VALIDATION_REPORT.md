# Phase II-05 through II-24 Validation Report

## Passed in this environment

- Phase II-01 through II-04 static verifier: PASS with expected V83/V84 predecessor warning.
- Phase II-05 through II-24 static verifier: PASS.
- Shell syntax for Phase II curl script: PASS.
- Python bytecode compilation for Phase II verifiers: PASS.
- YAML parse with multi-document support: PASS.
- JSON parse: PASS.
- Feature-flag contract: PASS; new modules remain default-off.
- Migration contract: PASS; V86-V90 are non-destructive and introduce no SHA-256 CHAR columns.
- Clean overlay contract: PASS during package preparation.

## Not run here

- Maven compile/test and Testcontainers integration tests are NOT_RUN because this execution environment cannot download Maven 3.9.12 from Maven Central.
- Live transfer rail, cross-border partner sandboxes, SFTP, MinIO/S3, email delivery and external AML screening are NOT_RUN and must be executed in CI/UAT.

## Commands to run after applying

```bash
python3 scripts/verify_phase_ii_01_04_static.py
python3 scripts/verify_phase_ii_05_24_static.py
PHASE_II_RUN_MAVEN=true ./apply-phase-ii-05-24.sh
```

Use strict predecessor mode before production certification:

```bash
python3 scripts/verify_phase_ii_01_04_static.py --strict-predecessors
```
