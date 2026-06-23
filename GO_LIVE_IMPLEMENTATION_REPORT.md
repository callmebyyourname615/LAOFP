# Switching Go-Live Critical Path — Implementation Report

Date: 2026-06-22  
Baseline Git commit: `95ea6ee`  
Source guide: `GO_LIVE_CRITICAL_PATH.md`

## Decision

Repository-side implementation has advanced substantially, but the system is **not yet Go-Live certified**.

The changed-files package implements the code, configuration, scripts, tests, CI workflows, and evidence templates that can be completed inside the repository. Live credential rotation, Git history rewriting, full Maven verification, UAT performance execution, backup/PITR drills, DR drills, infrastructure provisioning, and signed operational approval still require external runners/environments.

## Implemented scope

### P0.1 — Test and migration remediation

- Updated Flyway expectations to latest migration V97 and 94 migration files, with V88–V90 reserved.
- Added migration-profile `ObjectMapper` support required by Vault/webhook encryption backfill components.
- Fixed sanctions seed/provider UID handling and JDBC timestamp binding.
- Fixed operations integration-test cleanup ordering for suspension logs and participants.
- Updated Phase 53B, Phase II, Phase 43–54, and execute-and-verify static contracts for the current migration baseline.

### P0.2 — Secret exposure controls

- Removed tracked `.env.bak` and `new.txt`; deletions are listed in `GO_LIVE_DELETE_MANIFEST.txt`.
- Rebuilt `.env.example` and `.env.prod.example` as non-secret templates.
- Removed password fallbacks from Docker Compose and database initialization; missing secrets now fail fast.
- Removed the OAuth development signing-secret fallback; token signing fails closed when the secret is missing or too short.
- Added/updated the production environment contract and local secret generator.
- Reconstructed `docs/security/SECRET_ROTATION_CHECKLIST.md`.
- Redacted exposed credential values from the implementation guide.

Operator actions still required:

1. Rotate PostgreSQL, replication, application, Flyway, archive, and MinIO credentials in the actual secret store.
2. Execute the approved sensitive-history purge against a mirror clone.
3. Force-push only after review and approval.
4. Invalidate existing clones, CI caches, old images, and artifacts.
5. Produce a signed rotation/purge audit record.

### P0.3 — Performance proof preparation

- Added sustained 10K TPS scenario.
- Added burst 20K TPS scenario.
- Updated smoke to 100 TPS / 5 minutes.
- Updated sustained 2K TPS to 30 minutes.
- Updated soak test to 5K TPS / 8 hours.
- Enhanced `run-k6.sh` with scenario aliases, run metadata, safe evidence paths, and exit recording.
- Added `PERFORMANCE_SIGN_OFF_TEMPLATE.md`.

No performance result is claimed. These scenarios must be executed against production-like UAT capacity.

### P0.4 — Backup/PITR and DR preparation

- Added canonical DR scenario aliases.
- Added guarded region-failover runner.
- Added `DR_SIGN_OFF_TEMPLATE.md`.

No RPO/RTO result is claimed. Backup, restore, PITR, failover, and transaction-loss checks remain runtime work.

### P0.5 — SMOS User & Access Management

- Added migration `V97__smos_user_access_management.sql`.
- Added eight roles and a seeded role-permission matrix.
- Added operator user CRUD and status/role management.
- Added password authentication, TOTP MFA challenge, signed access tokens, refresh-token rotation, logout, and lockout.
- Added immediate user/role/permission revalidation on authenticated requests.
- Added maker-checker submission, approval, rejection, maker/checker separation, payload hashing, and tamper detection.
- Added settlement approval as the first controlled action.
- Added audit events and feature flags.
- Added fail-closed production startup validation and Kubernetes/Vault secret wiring.
- Added unit/integration tests and a static contract verifier.

SMOS remains feature-flagged and must pass the full Maven suite plus UAT operator acceptance before RC tagging.

### P1.6 — Critical dashboards

Added RBAC-protected read APIs for:

- Settlement dashboard
- Risk dashboard
- Cross-border dashboard

Each includes DTOs, JDBC aggregation service, controller, integration coverage, and static verification. Fine-grained SMOS permissions are enforced before method execution.

### P1.7 — Promotion eligibility

The project already contained a safe allow-listed JSON eligibility DSL. It was retained instead of being replaced with executable SpEL. Missing-value handling and tests were added. Promotion remains disabled by default pending product approval.

### CI/CD and immutable release controls

Added missing workflows for:

- Immutable application/migration image build and digest capture
- Backup image and backup deployment
- Release evidence
- Progressive deployment
- Rollback
- SLO gate
- Event schema gate
- Database maintenance deployment
- Participant certification
- Continuous resilience
- Phase 43–52 controls

## Validation completed

The following passed in the supplied repository:

- Production environment contract
- SMOS static contract
- Critical dashboard static contract
- Phase 1 static acceptance
- Phases 02–04 static acceptance
- Phases 05–07 static acceptance
- Phase 08 static acceptance
- Phases 13–22 static acceptance
- Phases 23–32 static acceptance
- Phases 33–42 static acceptance
- Phases 43–52 static acceptance
- Phase II-01–04 static contract
- Phase II-05–24 static contract
- Phase 53B schema alignment
- Phase 53C–53J static verification
- Phase 54A–54J static verification
- Phase 55 static contract
- Phase 56 static contract
- Phase 57 static contract
- Phase 58 static contract
- Repository hygiene verification
- Repository hygiene regression tests
- Local environment generator tests
- Changed YAML parse
- Changed shell syntax
- Changed Python syntax
- Changed JavaScript syntax
- Java syntax-form scan for changed files
- Git whitespace check

The aggregate `verify_all_static.py` runner did not complete within the sandbox execution limit, but each applicable verifier was run independently and passed.

## Validation not completed

### Maven

`./mvnw -DskipTests compile` could not run because the Maven Wrapper attempted to download Maven 3.9.12 from Maven Central and the sandbox has no internet/dependency cache.

Observed result:

```text
wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip
exit_code=1
```

Therefore, this package does **not** claim:

- Java dependency/type compilation success
- `./mvnw verify` success
- Testcontainers integration success
- Zero failures/zero errors in the complete test suite

Run these on the connected CI/UAT runner:

```bash
./mvnw -q clean verify
./scripts/execute-and-verify/00-run-all.sh
```

### Environment-dependent work

Still pending:

- Live secret rotation and Git history purge
- Production-like UAT infrastructure provisioning
- 10K sustained / 20K burst / 8-hour soak execution
- Settlement 500K benchmark
- Full backup verification
- Restore and PITR evidence
- DR scenario execution and measured RPO/RTO
- Alert routing/fire tests
- Vault rotation drill
- Participant/external endpoint certification
- Phase 54 signed certification manifest
- Phase 55 canary, cutover, hypercare, and BAU acceptance

## Recommended apply procedure

1. Back up the current branch and create a remediation branch.
2. Extract the changed-files ZIP at repository root.
3. Apply deletions from `GO_LIVE_DELETE_MANIFEST.txt`.
4. Review configuration and migration V97.
5. Run `./mvnw -q clean verify` on a connected runner.
6. Run all static gates and repository hygiene checks.
7. Deploy to UAT with SMOS disabled first; run payment regression tests.
8. Enable SMOS in UAT, bootstrap the first administrator through approved secret delivery, and complete operator acceptance.
9. Execute performance, backup/PITR, DR, security, and alert drills.
10. Tag an immutable RC only after all P0 evidence and approvals are complete.
