# Phases 55A–55J — Production Go-Live Delivery Notes

**Implemented:** 2026-06-19  
**Baseline:** Switching repository with Phase 53A–54J applied  
**Delivery type:** changed files only  
**Runtime execution:** intentionally not performed by this delivery

## Objective

Phase 55 converts the Phase 54 certified candidate into a controlled Production Go-Live
process. The framework is fail-closed and binds every action to one immutable identity:

- full 40-character Git commit;
- application image digest;
- migration image digest;
- release reference;
- release candidate ID.

A phase cannot reuse evidence from another release. Missing, stale, unsigned, symlinked,
identity-mismatched, tampered, `FAIL`, or `NOT_RUN` evidence blocks the next phase.

## Phase 55A — Immutable Release Candidate Assembly

Implemented:

- verifies application and migration image signatures with Cosign;
- verifies SLSA provenance attestations for both images;
- generates SPDX JSON SBOMs with Syft;
- renders deployment and migration manifests by immutable digest;
- rejects `latest`, unresolved placeholders, tags, and mutable image references;
- imports and verifies the Phase 54 certification manifest;
- creates a deterministic release manifest using `SOURCE_DATE_EPOCH`;
- hashes every included artifact;
- packages a deterministic `tar.gz` candidate;
- signs and verifies the final package blob;
- detects post-assembly modification.

## Phase 55B — Production Infrastructure Contract

Implemented fail-closed verification for:

- production Kubernetes context and access;
- PostgreSQL primary and read-replica roles;
- maximum database replication lag;
- `sslmode=verify-full` and production endpoint rules;
- Kafka authentication/TLS probe;
- Vault authentication and Transit-key probe;
- object-storage bucket versioning and Object Lock;
- custom S3-compatible endpoints through `OBJECT_STORAGE_ENDPOINT`;
- DNS, TCP, certificate chain, hostname and expiry checks;
- production external-dependency connectivity;
- host time synchronization;
- machine-readable production environment contract;
- prohibited localhost, mock, placeholder and weak-secret values.

## Phase 55C — Production Migration Dry Run

Implemented:

- requires an anonymized production-like snapshot attestation;
- verifies the attestation signature and release identity;
- requires two approvers and a snapshot age no greater than seven days;
- restores only to an isolated dry-run database;
- verifies the starting Flyway version, defaulting to V82;
- captures pre-migration reconciliation in one read-only repeatable-read snapshot;
- runs the digest-pinned migration image to exactly V83;
- measures migration duration and validates bounded completion;
- verifies idempotent migration rerun;
- validates application startup with `ddl-auto=validate`;
- validates previous-version compatibility after V83;
- compares financial and operational invariants;
- restores the pre-migration checkpoint and verifies rollback state.

The database schema strategy remains forward-fix. The rollback exercise restores the isolated
dry-run database and does not claim that destructive schema rollback is safe in Production.

## Phase 55D — Data Reconciliation and Cutover Baseline

Implemented:

- captures all configured transaction, balance, settlement, outbox, webhook and archive
  aggregates in one `REPEATABLE READ READ ONLY` transaction;
- applies statement and lock timeouts;
- enforces configured equality, zero, non-decreasing and monotonic invariants;
- records release identity and snapshot metadata;
- writes SHA-256 evidence;
- archives the baseline with KMS encryption and Object Lock `COMPLIANCE` mode;
- requires retention to be timezone-aware and at least 30 days in the future;
- supports AWS S3 and configured S3-compatible endpoints;
- verifies the persisted retention receipt.

## Phase 55E — Production Access and Security Hardening

Implemented:

- renders digest-pinned application and migration workloads;
- applies namespace ResourceQuota and LimitRange;
- applies default-deny ingress and egress controls;
- permits only explicit production dependency CIDRs;
- rejects `0.0.0.0/0` and `::/0` dependency allowlists;
- enforces non-root execution, read-only root filesystem, seccomp and dropped capabilities;
- verifies application and Flyway database identities are separated;
- verifies the application role has no DDL privilege and owns no database objects;
- verifies runtime service accounts cannot modify deployments or read Secrets;
- verifies a signed, release-bound secret-rotation attestation;
- requires two approvers and short-lived break-glass access.

Security resources are server-side dry-run validated before any apply action. A failed preflight
cannot proceed to mutation.

## Phase 55F — Go-Live Command Center

Implemented:

- master Go-Live, cutover and rollback runbooks;
- command-center ownership and escalation matrix;
- required Release Commander, Application, Database, Kafka, Security, Operations,
  Business Validation and Rollback Authority roles;
- cutover-plan validation;
- contact-matrix attestation;
- signed command-center approval;
- two-person approval and freshness controls;
- cryptographic binding of approval to the cutover plan and contact matrix hashes;
- abort criteria, evidence requirements and rollback commands.

## Phase 55G — Production Canary Deployment

Implemented production safety sequence:

1. verify prerequisites and production change window;
2. verify the signed 5% promotion decision against current evidence;
3. render secure canary ingress and dependency NetworkPolicy;
4. run the digest-pinned migration Job;
5. assert Flyway V83;
6. deploy the digest-pinned canary;
7. wait for readiness before traffic;
8. shift exactly 5% traffic;
9. observe for the configured minimum period;
10. verify comprehensive Prometheus gates;
11. run production synthetic transactions;
12. compare against the immutable cutover baseline.

The canary ingress requires a real production hostname, TLS Secret, client-CA Secret and
IngressClass. Example/local hosts are rejected. Any failure triggers best-effort traffic reset
to zero and canary scale-down.

## Phase 55H — Controlled Traffic Cutover

Phase 55G owns 5%; Phase 55H promotes only:

- 25%;
- 50%;
- 100%.

Each stage requires:

- a new signed decision bound to the immediately preceding stage evidence;
- a fresh change-window decision;
- minimum observation of 30, 30 and 60 minutes respectively;
- P95/P99, error-rate, Kafka lag, outbox backlog, Hikari utilization,
  settlement, webhook, reconciliation and critical-alert gates;
- synthetic production transaction validation;
- a fresh repeatable-read reconciliation snapshot;
- comparison with the immutable Phase 55D baseline.

At 100%, the stable Deployment is changed to the candidate digest and annotated with the
release identity. Failure automatically restores canary weight to zero and rolls stable back
to the exact previous digest when it was changed.

## Phase 55I — Hypercare and Production Validation

Implemented:

- minimum hypercare duration policy;
- observation validation for required service and financial signals;
- alert delivery and critical-alert closure checks;
- incident register validation;
- zero open critical incidents;
- high-severity ownership and mitigation controls;
- production reconciliation against the cutover baseline;
- generated hypercare summary and readiness decision;
- release-bound evidence for handover.

## Phase 55J — Go-Live Closure and BAU Handover

Implemented:

- signed Business acceptance;
- signed Operations acceptance;
- signed Security acceptance;
- two approvers for every acceptance domain;
- acceptance binding to hypercare summary, known-issues register and post-implementation
  review SHA-256 values;
- rejection of open critical known issues;
- owner, target date and two-person risk acceptance for open high issues;
- final evidence secret scan;
- operational-acceptance manifest and checksum inventory covering Phase 55A–55J.

## Primary repository commands

Apply and validate the changed files:

```bash
chmod +x apply-phases55a-55j.sh
./apply-phases55a-55j.sh
```

Run framework tests during apply:

```bash
PHASE55_RUN_FRAMEWORK_TESTS=true ./apply-phases55a-55j.sh
```

Run compatibility gates for Phase 53B, 53C–53J and 54A–54J:

```bash
PHASE55_RUN_PRIOR_STATIC_GATES=true ./apply-phases55a-55j.sh
```

Show runtime status:

```bash
scripts/golive/run_phase55_golive.sh status
```

Execute one approved phase:

```bash
scripts/golive/run_phase55_golive.sh 55A
```

The environment variables, confirmations, signatures and evidence inputs for each phase are
listed in `docs/runbooks/PHASE55_PRODUCTION_GOLIVE.md` and
`.github/workflows/phase55-golive.yml`.

## Safety statement

The apply script never connects to Production, changes traffic, runs Flyway, modifies RBAC,
rotates secrets, performs cutover, or creates an operational acceptance. Those actions require
explicit per-phase environment selection, exact confirmation strings, protected runner access,
signed inputs and successful prerequisites.

Repository implementation is complete. Production remains **NO-GO** until Phase 55A–55J
are executed in order and every result is `PASS` for the same immutable release identity.
