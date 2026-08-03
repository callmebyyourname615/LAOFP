# Production Go-Live Readiness Guide

> **Current status: NO-GO**
>
> UAT endpoint coverage and isolated backup tests are good progress, but
> production controls, real recovery validation, security hardening, and formal
> approvals remain incomplete.

## Phase 0: Go-Live Scope and Ownership

**Tracking artifact:** copy
[`PRODUCTION_LAUNCH_MANIFEST.example.yaml`](templates/PRODUCTION_LAUNCH_MANIFEST.example.yaml)
to a controlled, non-secret release-evidence location and validate it with:

```bash
python3 scripts/golive/validate_production_launch_manifest.py \
  --manifest /controlled/evidence/production-launch-manifest.yaml
```

Do not use the example manifest as production evidence.

- [ ] Define production launch scope: participants, channels, limits,
  currencies, and settlement model.
- [ ] Identify Business Owner, Engineering Lead, QA Lead, SecOps, SRE Lead,
  and Change Manager.
- [ ] Define RPO, RTO, availability SLO, payment-success SLO, and
  reconciliation SLA.
- [ ] Create production change ticket, rollback owner, and communication plan.
- [ ] Freeze release candidate commit and immutable container image digest.
- [ ] Confirm UAT, staging, and production are fully separated.

**Exit criteria:** approved launch scope, owners, risk register, and release
candidate.

## Phase 1: Security Foundation

- [ ] Move secrets into Vault or an approved secret manager:
  - [x] Verify ExternalSecret delivery mapping against the production contract
    (`EXTERNAL_SECRET_DELIVERY_CONTRACT`, 2026-07-22; static manifests only).
  - [ ] Provision and verify the real production Vault endpoint, Kubernetes
    authentication, policies, and ExternalSecret synchronization.
  - [ ] Database credentials.
  - [ ] JWT and OAuth signing secrets.
  - [ ] mTLS private keys.
  - [ ] RTGS credentials.
  - [ ] Backup age private identity.
  - [ ] Object-storage credentials.
- [ ] Rotate credentials exposed in terminals, logs, chat history, or old
  `.env` files.
- [ ] Remove production secrets from Git, images, runtime evidence, and
  application logs.
  - [x] Redact runtime evidence and verify it contains no JWT or private-key
    material (`RUNTIME_EVIDENCE_SECRET_REDACTION`, 2026-07-22, 0 findings).
  - [x] Verify current tracked working tree contains no prohibited paths or
    literal secrets (`REPOSITORY_HYGIENE_WORKING_TREE`, 2026-07-22, 0 findings).
  - [ ] Purge prohibited paths and secrets from Git history, then pass a full
    Gitleaks history scan.
  - [ ] Verify container images and application logs are free of secrets.
    - [x] Pass static sensitive-logging policy
      (`SENSITIVE_LOGGING_STATIC_POLICY`, 2026-07-22).
- [ ] Enforce MFA for privileged users.
  - [x] Pass SMOS MFA, refresh-token revocation, lockout, and RBAC integration
    certification (`SMOS_MFA_ENFORCEMENT_INTEGRATION`, UAT, 2026-07-22;
    9 tests passed).
- [ ] Disable bootstrap and default admin accounts in production.
- [ ] Apply least-privilege RBAC and separate maker/checker accounts.
- [ ] Enforce mTLS for member-bank and internal privileged APIs.
- [ ] Enable OAuth/JWT validation, token expiry, revocation, and session
  controls.
- [ ] Enforce webhook HTTPS, hostname allowlist, egress proxy, and SSRF
  protection.
- [ ] Perform SAST, dependency scan, container scan, SBOM generation, and
  image signing.
- [ ] Complete penetration testing for RBAC, MFA, IDOR, file upload, webhooks,
  API abuse, and mTLS.

**Exit criteria:** no critical vulnerabilities; secrets are externally managed;
security approval is recorded.

## Phase 2: Production Infrastructure

- [ ] Provision production network with firewall and private service access.
- [ ] Separate production database, Kafka/Redpanda, MinIO, certificates, and
  credentials from UAT.
- [ ] Configure TLS for PostgreSQL, Kafka, internal services, and ingress.
- [ ] Enable database primary, read replica, archive replica, and health
  monitoring.
- [ ] Configure Kafka/Redpanda authentication and TLS.
- [ ] Configure production object storage with encryption and restricted access.
- [ ] Configure observability: Prometheus, Grafana, logs, tracing, and alert
  routing.
- [ ] Configure capacity limits, autoscaling policy, disk monitoring, and
  resource quotas.
- [ ] Test application restart, database restart, broker restart, and node
  failure.
- [ ] Test rollback to the previous immutable image.

**Exit criteria:** production infrastructure attestation and migration dry-run
pass.

## Phase 3: Backup, PITR, and DR

- [ ] Provision the matching backup age private identity from secret manager.
- [ ] Verify the identity matches `BACKUP_AGE_RECIPIENT`.
- [ ] Configure real primary and secondary object storage in separate fault
  domains.
- [ ] Enable encrypted full backups and continuous WAL archival.
- [ ] Verify backup checksum and tamper rejection.
- [ ] Verify secondary replication and secondary restore fallback.
- [ ] Apply and verify retention lifecycle policy.
- [ ] Restore a real encrypted backup into isolated infrastructure.
- [ ] Perform real point-in-time recovery using archived WAL.
- [ ] Record measured RPO and RTO against approved targets.
- [ ] Run database failover and DR-region recovery exercise.
- [ ] Document restore, failover, and failback runbooks.
- [ ] Schedule periodic backup verification and restore drills.

**Exit criteria:** real backup restore and PITR are proven, not only isolated
smoke tests.

## Phase 4: Payment and Settlement Integrity

- [ ] Verify transfer idempotency for retries and duplicate requests.
- [ ] Verify outbox retry, stuck-event recovery, and dead-letter handling.
- [ ] Verify transaction state transitions and audit history.
- [ ] Verify pool hold, confirmation, release, and balance invariants.
- [ ] Reconcile source debit, destination credit, settlement position, and
  ledger amount.
- [ ] Test DRS, disputes, refunds, reversals, and maker/checker approvals.
- [ ] Complete a large-volume settlement test such as `SETTLEMENT-500K`.
- [ ] Test end-of-day and intraday reconciliation mismatch handling.
- [ ] Define automatic financial-mismatch alert and escalation process.
- [ ] Verify no duplicate posting under timeout, retry, or callback duplication.

### RTGS Integration

- [ ] Obtain real RTGS interface specification and connectivity approval.
- [ ] Implement authenticated RTGS callback:
  - [ ] Partner mTLS.
  - [ ] Request signature or HMAC.
  - [ ] Strict source network allowlist.
  - [ ] Replay protection.
  - [ ] Idempotency handling.
  - [ ] Full callback audit trail.
- [ ] Keep RTGS callback disabled until integration credentials are validated.
- [ ] Test RTGS success, rejection, timeout, duplicate callback, invalid
  signature, and delayed callback.
- [ ] Allow `SETTLED` only after valid RTGS confirmation.
- [ ] Verify settlement reports and CAMT.054 generation after confirmed
  settlement.

**Exit criteria:** financial integrity and RTGS settlement confirmation are
proven end-to-end.

## Phase 5: Performance and Reliability

- [ ] Define production SLOs for latency, availability, payment success,
  settlement, and recovery.
- [ ] Complete baseline load test.
- [ ] Complete `PERF-10K`.
- [ ] Complete `PERF-BURST-20K`.
- [ ] Test mixed workload: inquiries, transfers, retries, settlement,
  reporting, and webhooks.
- [ ] Test backpressure and queue saturation.
- [ ] Test database connection-pool exhaustion.
- [ ] Test Kafka/Redpanda lag and consumer recovery.
- [ ] Test rate limits and abuse protection.
- [ ] Verify graceful degradation during downstream connector failure.
- [ ] Verify no unbounded backlog, duplicate posting, or financial mismatch
  under load.

**Exit criteria:** performance results meet approved SLOs with capacity
headroom.

## Phase 6: Operations and Monitoring

- [ ] Create dashboards for transfer success, latency, outbox, settlement,
  reconciliation, and RTGS status.
- [ ] Alert on failed outbox, dead letters, queue lag, replica lag, disk
  pressure, and certificate expiry.
- [ ] Alert on backup failure, WAL archive delay, failed restore drill, and
  secondary replication failure.
- [ ] Alert on settlement mismatch, duplicate posting, unresolved dispute, and
  RTGS callback failure.
- [ ] Assign each alert an owner, severity, runbook, escalation path, and test
  record.
- [ ] Create on-call schedule and incident communication channel.
- [ ] Complete incident, rollback, settlement exception, and DR runbooks.
- [ ] Verify audit-log retention and evidence integrity.

**Exit criteria:** alert lifecycle and operational command center are tested.

## Phase 7: Release Governance

- [ ] Run clean Maven build and automated test suite.
- [ ] Verify repository integrity and dependency provenance.
- [ ] Generate release SBOM and signed image digest.
- [ ] Run migration dry-run against production-like data.
- [ ] Confirm rollback plan and rollback image.
- [ ] Record evidence for:
  - [ ] `BUILD-MAVEN-VERIFY`.
  - [ ] `REPOSITORY-VERIFY`.
  - [ ] `SECRET-ROTATION`.
  - [ ] `SMOS-SECURITY`.
  - [ ] `BACKUP-RESTORE`.
  - [ ] `PITR`.
  - [ ] `DR`.
  - [ ] `FINANCIAL-INTEGRITY`.
  - [ ] `SETTLEMENT-500K`.
  - [ ] `PERF-10K`.
  - [ ] `PERF-BURST-20K`.
  - [ ] `ALERT-LIFECYCLE`.
- [ ] Obtain approvals:
  - [ ] Business Owner.
  - [ ] Engineering Lead.
  - [ ] QA Lead.
  - [ ] Change Manager.
  - [ ] SecOps.
  - [ ] SRE Lead.

**Exit criteria:** formal readiness decision changes from `PREPARED` to `GO`.

## Phase 8: Canary and Hypercare

- [ ] Deploy immutable release candidate.
- [ ] Start canary at 5%.
- [ ] Verify success rate, latency, queue lag, errors, and financial
  reconciliation.
- [ ] Promote to 25%.
- [ ] Promote to 50%.
- [ ] Promote to 100%.
- [ ] Abort immediately for financial mismatch, duplicate posting, security
  breach, migration inconsistency, or unbounded backlog.
- [ ] Start 14-day hypercare.
- [ ] Complete Day 1 reconciliation.
- [ ] Complete Day 3 settlement review.
- [ ] Complete Day 7 weekly reconciliation.
- [ ] Complete Day 14 exit review.

**Exit criteria:** hypercare exit criteria are approved and there are no
unresolved critical incidents.

## Recommended Next Task

- [ ] **Provision the real backup age identity and run real encrypted backup
  restore plus PITR.**

This is the highest-priority remaining task because production launch requires
proven recovery of real backup data.
