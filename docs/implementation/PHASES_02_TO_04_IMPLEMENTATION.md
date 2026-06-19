# Implementation Evidence — Phases 02–04

Baseline for this delivery: Phase 01 (Kubernetes Flyway Migration Job).
This change set contains only files added or modified after that baseline.

## Phase 02 — Immutable Container Image

Implemented:

- Container tag is the full tested Git commit SHA.
- Build metadata is exposed through Spring Boot `build-info` and OCI labels.
- The registry digest is captured after push and stored as a workflow artifact.
- Kubernetes Deployment and migration Job templates require an `@sha256:` image.
- The deploy workflow renders the same digest into both manifests.
- The migration Job must complete before the Deployment rollout starts.
- Failed rollout restores the previous image; a manual rollback workflow is also available.

Verification:

```bash
python3 scripts/verify_phase1_static.py
python3 scripts/verify_phases_02_04_static.py
```

Runtime evidence to retain from staging:

1. `image-metadata-<git-sha>` workflow artifact.
2. Migration Job logs.
3. Deployment image returned by `kubectl get deployment`.
4. Successful rollback workflow execution.

## Phase 03 — Full CI Test Gate and Security Scans

Implemented:

- CI runs the complete `./mvnw test` suite; no `-Dtest=` allowlist.
- Surefire reports are uploaded even when tests fail.
- Packaging is blocked until tests and the reusable security workflow pass.
- Gitleaks secret scan.
- OWASP Dependency Check with optional NVD API key.
- GitHub Dependency Review for pull requests.
- Trivy filesystem and container scans, failing on HIGH/CRITICAL findings.
- CodeQL Java/Kotlin SAST.
- Branch-protection helper script for required checks.

Repository/organization configuration still required:

- Add `NVD_API_KEY` as an Actions secret for reliable OWASP updates.
- Enable GitHub code scanning/SARIF upload.
- Run `scripts/configure_branch_protection.sh` with an admin token.

## Phase 04 — Webhook Secret Encryption

Implemented:

- AES-256-GCM envelope encryption with a random DEK for each webhook secret.
- Vault Transit wraps/unwraps DEKs in staging/production.
- A local AES key wrapper exists only for development and tests.
- `V43` expands the schema; Java backfill encrypts existing values; `V44` refuses
  to drop plaintext until every row is encrypted.
- Runtime Flyway auto-migration is disabled for staging and production.
- New registrations persist only ciphertext, Vault key ID, secret version, and hash.
- Rotation uses a pessimistic database lock and a configurable grace period.
- During grace, outbound requests include current and previous HMAC signatures.
- Vault/KMS failure fails closed and schedules a retry; no plaintext/hash fallback.
- Every successful rotation writes `WEBHOOK_SECRET_ROTATED` to the audit log.
- Production startup rejects local/static encryption and an incomplete V44 migration.

Acceptance mapping:

| Acceptance condition | Evidence |
|---|---|
| No plaintext in DB after migration | `V44__drop_webhook_secret_plain.sql` and migration integration test |
| HMAC valid during rotation grace | `WebhookHttpSenderTest` |
| KMS unavailable fails closed | `WebhookDeliveryServiceTest` |
| Rotation is audited | `WebhookSecretRotationServiceTest` |
| Existing rows are encrypted | `MigrationApplicationIntegrationTest` |

## Validation limitation

The repository's Maven Wrapper requires downloading Maven 3.9.12. The delivery
sandbox could not access that distribution, so the full Maven suite was not run
locally. YAML/XML/shell/static acceptance checks pass. The authoritative compile,
unit-test, Testcontainers migration test, image build, and security scans run in CI.
