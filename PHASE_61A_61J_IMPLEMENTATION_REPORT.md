# Switching — Phase 61A–61J Implementation Report

**Implementation date:** 2026-06-23  
**Baseline:** uploaded `Switching.zip`, Git HEAD `8a03938e`  
**Delivery model:** cumulative changed-files package containing the prior Phase 60 implementation plus Phase 61, because the uploaded baseline did not contain the complete Phase 60 delivery.

## Executive status

| Phase | Repository implementation | Runtime certification |
|---|---|---|
| 61A Build/Test closure | Implemented | **PREPARED** — Maven unavailable in isolated environment |
| 61B Migration/Data Integrity | Implemented | **PREPARED** — Testcontainers/DB execution required |
| 61C UAT Infrastructure Contract | Implemented | **PREPARED** — live UAT endpoints required |
| 61D SMOS Security Hardening | Implemented | **PREPARED** — Maven/E2E security suite required |
| 61E Dashboard/Promotion Readiness | Implemented | **PREPARED** — Maven/UAT data acceptance required |
| 61F Secrets/Supply Chain | Implemented | **PREPARED** — SecOps/Git remote/Cosign actions required |
| 61G Performance/Capacity | Implemented | **PREPARED** — 100/2K/10K/20K/8h UAT runs required |
| 61H Settlement/Reconciliation | Implemented | **PREPARED** — 500K UAT benchmark required |
| 61I Backup/PITR/HA/DR/Alerts | Implemented | **PREPARED** — destructive UAT drills required |
| 61J Evidence/RC Gate | Implemented | **PREPARED** — requires 61A–61I PASS and signed approvals |

`PREPARED` means the automation, guards, validators and evidence contracts are implemented. It does not mean that UAT/runtime certification has passed.

## 61A — Build and Test Green Closure

Implemented:

- Phase 61 orchestrator with `--preflight`, `--repo` and `--full` modes.
- Fresh Surefire/Failsafe report certification.
- Minimum test-count and zero failure/error enforcement.
- Stale-report rejection tied to the current source revision.
- Three-repeat critical-test stability gate for flaky-test detection.
- Machine-readable phase result JSON with artifact hashes.
- Git tree-state lookup bounded by timeout and tracked-file scope to prevent preflight hangs on large delivery workspaces.
- GitHub Actions Phase 61 certification workflow.
- Phase 61 integration in `scripts/execute-and-verify/00-run-all.sh`.

Runtime command:

```bash
./mvnw clean verify
scripts/phase61/run_phase61.sh --repo
```

## 61B — Migration and Data Integrity Certification

Implemented:

- Migration inventory validator with SHA-256 per migration.
- Clean-install and migration-application targeted certification runners.
- Financial integrity regression runner.
- V101 SMOS security-hardening migration and integration test.
- Current baseline expectation: 90 migration files through V101.
- Reserved/missing baseline versions are explicitly recorded rather than silently ignored.

V101 adds:

- Participant scope on SMOS users.
- Password/MFA/lock timestamps.
- Refresh-session family and rotation lineage.
- Refresh-token replay controls.
- Session and security permissions.

### Baseline inconsistency discovered

The uploaded ZIP does not contain Phase II migrations V91–V96 or their expected source implementation. The Phase II static verifier fails at missing `V92__promotion_management.sql`. The repository inventory currently contains 90 migrations through V101, with gaps V88–V96 and V98–V99.

This was not hidden by weakening the global verifier. The authoritative Phase II branch/ZIP must be merged before final migration certification. Already-applied migration numbers must not be renumbered.

## 61C — UAT Deployment and Infrastructure Contract

Implemented:

- Versioned UAT infrastructure contract.
- Required environment-variable validation.
- PostgreSQL, Kafka, Vault and object-storage TCP probes.
- HTTPS-only UAT endpoint requirement.
- Application and migration image digest validation.
- Placeholder, `latest` and `SNAPSHOT` rejection.
- Time-synchronization/clock-skew check.
- Machine-readable probe evidence.
- Explicit UAT-only execution safety guards.

## 61D — SMOS Production Security Hardening

Implemented:

- Configurable password policy:
  - minimum length;
  - upper/lower/digit/symbol requirements;
  - username/email-local-part rejection;
  - common weak-pattern rejection.
- Mandatory MFA enforcement when configured.
- Account lock timestamps and failed-login timestamps.
- MFA enrollment timestamp.
- TOTP failure lockout.
- JWT issuer, audience, algorithm, token ID, issued-at, not-before, expiry and maximum-lifetime validation.
- Participant ID carried in the operator token and authenticated context.
- `PARTICIPANT_ADMIN` is deliberately **not** mapped to PSP `ROLE_BANK`; operator and PSP credentials remain separate trust domains.
- Refresh-token family rotation.
- Refresh-token reuse detection that revokes the complete session family.
- Active-session inventory.
- Revoke one session family and revoke all sessions.
- Role/status changes revoke refresh sessions.
- Disabled-user and current-role checks on every SMOS request.
- New session/security permissions.
- Migration, password-policy, token and session-security tests.

## 61E — Operations Dashboard and Promotion Readiness

Implemented:

- Database-local statement timeout guard for Settlement, Risk and Cross-Border dashboards.
- Read-only transaction boundaries.
- Bounded result sets for high-cardinality dashboard queries.
- Stable dashboard static/acceptance contracts.
- Promotion remains disabled by default.
- Promotion eligibility remains a bounded non-executable JSON DSL.
- DSL limits, allowed fields/operators, numeric validation and malformed-input tests.

## 61F — Secret Rotation and Supply-Chain Closure

Implemented:

- Six-secret canonical inventory.
- Signed supply-chain/rotation attestation validator.
- Enforcement for:
  - old credentials disabled;
  - Git history purged;
  - caches and old clones invalidated;
  - all branches/tags scanned;
  - Gitleaks/Trivy/Grype PASS;
  - zero Critical/High vulnerabilities unless handled outside this strict gate;
  - SBOM and provenance hashes;
  - Cosign verification;
  - digest-pinned deployment manifests.
- Repository preflight supports the two declared pending deletions without treating absent working-tree files as still exposed.

Files that must be deleted and committed:

```text
.env.bak
new.txt
```

Actual credential rotation and history rewrite remain operator/SecOps actions.

## 61G — Performance and Capacity Certification

Implemented:

- Constant-arrival-rate 100 TPS, 2K TPS, 10K TPS and 5K TPS soak scenarios.
- Ramping-arrival-rate 20K TPS burst scenario.
- Thresholds for errors, p95, dropped iterations and checks.
- Verification of **observed request rate**, not only configured target rate.
- Capacity evidence capture before and after load.
- Required Kubernetes/Prometheus evidence directories and metadata.
- CPU, memory, GC, HPA, restart, replica-lag, disk and thread-pool attestation checks.
- Removed the previous neutral/fake settlement placeholder from the performance verifier. Settlement is independently certified in 61H.

Required full UAT scenarios:

| Scenario | Minimum observed rate | Main threshold |
|---|---:|---|
| Smoke | 95 TPS | p95 < 200 ms, 0% failures |
| Sustained 2K | 1,900 TPS | p95 < 300 ms, failures < 0.1% |
| Sustained 10K | 9,500 TPS | p95 < 500 ms, failures < 0.1% |
| Burst 20K | 18,000 TPS average | p95 < 750 ms, failures < 0.5% |
| Soak 8h | 4,750 TPS | no leak, no unbounded lag |

## 61H — Settlement and Reconciliation Scale Proof

Implemented:

- 500,000-transaction settlement benchmark.
- Intentional second batch call to prove rebatch idempotency.
- Database-side verification of:
  - exactly 500,000 eligible transactions;
  - exactly 1,000,000 debit/credit settlement legs;
  - position transaction-leg total equals 1,000,000;
  - no missing debit or credit leg;
  - no duplicate settlement posting;
  - zero debit/credit balance mismatch;
  - no negative reporting counter;
  - no undelivered/failed performance outbox record.
- Reconciliation CSV parser verifies every check and requires the canonical check set.
- Signed SLA, finance-control and settlement-lead attestation.
- `PERF_EXPECTED_TX_COUNT=500000` is now mandatory during post-benchmark reconciliation.

## 61I — Backup, PITR, HA, DR and Alert Drills

Implemented:

- UAT-only destructive execution guard.
- Runtime evidence orchestrator that requires topology-specific command hooks for:
  - full backup;
  - backup verification;
  - PITR restore;
  - PostgreSQL primary failover;
  - PostgreSQL failback;
  - Vault leader failover;
  - object-storage node failover.
- Existing controlled DR suite for:
  - application pod kill;
  - Kafka broker failure;
  - network partition;
  - external API timeout;
  - deployment rollback.
- Synthetic Alertmanager firing/observation/resolution drill.
- Required runtime evidence paths and SHA-256 inventory.
- Evidence verifier rejects attestation-only claims when corresponding runtime logs are missing.
- RPO/RTO and zero financial-loss enforcement.

Topology-specific commands must be supplied explicitly because the repository cannot safely guess commands for RDS/Patroni, Vault HA, MinIO or cloud object-storage topology.

## 61J — UAT Evidence Bundle and Release-Candidate Gate

Implemented:

- Exact Phase 61A–61J phase-result inventory.
- All phases must be `PASS`; `PREPARED` cannot enter the RC gate.
- Git commit equality across phase results.
- Application and migration image digest binding.
- SHA-256 and size verification for every evidence artifact.
- Approval contract for Engineering, QA, Security, SRE, Product and Change Management.
- Manifest path traversal protection.
- Synthetic positive tests for capacity, settlement, resilience and evidence-manifest tooling.

## Validation completed in the delivery environment

Passed:

- Phase 61 static contract.
- Phase 60 static contract.
- SMOS static contract.
- Critical dashboard static contract.
- Alert/runbook contract: 58 unique alerts.
- Production environment template contract.
- Repository hygiene with declared pending deletions.
- Bash syntax.
- Python compilation.
- JSON/YAML parsing.
- JavaScript syntax.
- Git whitespace validation.
- Java source parse scan: no syntax diagnostics beyond missing external classpath symbols.
- Synthetic capacity verifier test.
- Synthetic 500K settlement-evidence verifier test.
- Synthetic resilience-evidence verifier test.
- Synthetic immutable evidence-manifest build and verification.

Not certified in this environment:

- Maven compile/verify. Maven Wrapper could not download Maven 3.9.12 from Maven Central.
- Testcontainers and PostgreSQL migration execution.
- UAT network/TLS probes.
- Secret rotation/history rewrite.
- Performance, settlement, backup, PITR, HA/DR and alert runtime drills.
- Final UAT approvals.

## Required next execution order

1. Merge the authoritative Phase II V91–V96 source, or explicitly correct the project documentation if it was never delivered.
2. Apply this package and commit deletion of `.env.bak` and `new.txt`.
3. Run `./mvnw clean verify` in connected CI.
4. Run `scripts/phase61/run_phase61.sh --repo`.
5. Provision/validate UAT and execute `--full` with signed operator attestations.
6. Do not start Phase 54 until every Phase 61 result is `PASS` and the 61J manifest verifies.
