# Switching API — Phases 13–22 Delivery Notes

Created: 2026-06-18
Baseline: assembled Phase 1–12 source
Delivery type: changed and added files only

## Implemented phases

### Phase 13 — Release integrity and provenance

- Deterministic release evidence JSON with Git commit, immutable image digest, file size and SHA-256.
- Path traversal, symlink, duplicate artifact, missing artifact, size and hash verification.
- GitHub workflow checks out the exact 40-character commit, runs the Maven `verify` lifecycle, builds evidence and retains it for 365 days.
- Evidence schema and local tamper-detection verifier.

### Phase 14 — Progressive canary delivery

- Digest-pinned canary Deployment, Service, Ingress, ServiceMonitor and NetworkPolicy.
- Migration gate before application rollout.
- Canary stages at 5%, 25% and 50%, followed by stable promotion.
- Prometheus gates for HTTP 5xx ratio and P95 latency.
- Automatic canary rollback on metric, readiness or rollout failure.
- Stable and canary metrics carry an explicit `release_track` label.

### Phase 15 — SLO and error-budget governance

- Availability and latency recording rules.
- 30-day error-budget remaining calculation.
- Multi-window burn-rate alerts.
- Production workflow blocks standard releases when the remaining budget is below policy.
- Reliability, security and emergency exceptions require an explicit approved exception input.

### Phase 16 — Event schema governance

- Versioned outbox event envelope with schema name, version and UUID event ID.
- Producer emits version 1; consumer validates before processing.
- Production and staging reject legacy messages.
- JSON Schema registry, immutable baseline and backward-compatibility checker.
- CI schema compatibility workflow and unit test.

### Phase 17 — Durable dead-letter quarantine and controlled replay

- Failed outbox records are durably quarantined before Kafka listener success is returned.
- Payload SHA-256 integrity verification.
- Idempotent quarantine by event ID and failure counter.
- Four-eyes replay request and approval.
- Replay and discard require break-glass authorization.
- Synchronous Kafka acknowledgement before marking a record replayed.
- Full state-transition audit events.

### Phase 18 — Database lifecycle and maintenance

- Dedicated maintenance credentials through External Secrets.
- Preflight checks, advisory locking, vacuum/analyze and maintenance evidence table.
- Digest-pinned maintenance image and deployment workflow.
- Read-only root filesystem, non-root runtime and bounded temporary storage.
- Maintenance success/failure and age metrics with alerts.

### Phase 19 — Retention and legal hold

- Legal-hold request, separate approval, release request and separate release approval.
- `ACTIVE` and `RELEASE_REQUESTED` holds continue to block retention.
- Safe table identifier validation.
- Partition drop and retention evidence are executed in one transaction.
- Retention execution log records blocked, successful and failed actions.

### Phase 20 — Privileged break-glass access

- Four-eyes request and approval with a maximum 24-hour approval window.
- Random one-time-returned token; only SHA-256 token hash and short prefix are stored.
- Token is bound to requester, expiry and maximum usage count.
- Approval response disables HTTP caching.
- Automatic expiry and audit events for every lifecycle transition.
- Break-glass enforcement is installed after ordinary authentication, mTLS and HMAC filters.

### Phase 21 — Four-eyes configuration changes

- Controlled changes for participant status, connector enable/force-reject and routing-rule enablement.
- Request captures previous value, desired value and SHA-256 canonical payload.
- Requester cannot approve their own change.
- Drift detection marks requests stale instead of overwriting a newer value.
- Direct participant, connector and routing PATCH operations are denied.
- New participants default to `INACTIVE`.
- Execution of dangerous configuration changes requires break-glass authorization.

### Phase 22 — Participant certification and continuous resilience

- Versioned participant certification plan and HTTPS runner.
- Bounded 1 MiB response evidence, SHA-256 evidence file and no response payload persistence.
- Bank-code/path input validation and GitHub workflow injection controls.
- Latest certification result governs activation; a newer FAIL supersedes an older PASS.
- Certification evidence cannot be expired, future-dated or valid for more than 370 days.
- Participant activation requires the latest non-expired PASS.
- Metrics and alerts for active participants without certification and certifications expiring within 30 days.
- Continuous resilience workflow schedules certification execution.

## Security and operational details

- Application runtime user remains `switching` with UID/GID 10001, matching Kubernetes security contexts.
- Canary and maintenance workloads use digest placeholders that must be rendered before apply.
- Release workflows validate full lowercase Git SHA and `sha256:<64 hex>` digest values.
- Sensitive participant-certification details are sanitized before persistence and audit data never contains the API key or response body.
- Certification and release evidence use portable, relative paths and detect tampering after bundle relocation.

## Validation completed in this environment

- Phase 1 static acceptance: PASS.
- Phase 2–4 static acceptance: PASS.
- Phase 5–7 static acceptance: PASS.
- Phase 8 static acceptance: PASS.
- Phase 13–22 static acceptance: PASS.
- YAML multi-document parsing: PASS.
- JSON and XML parsing: PASS.
- Shell syntax validation: PASS.
- Python byte-code compilation: PASS.
- Java syntax parse: 740 source/test files PASS.
- Event schema compatibility and release-evidence tamper tests: PASS.
- Mock Prometheus canary gate: PASS.
- Mock Prometheus 30-day SLO gate: PASS.
- Mock HTTPS participant certification suite: PASS, 5/5 steps.

## Environment verification still required

This environment cannot download the Maven 3.9.12 wrapper distribution because Maven Central DNS/network access is unavailable. The following remain authoritative staging/CI gates:

1. `./mvnw clean verify` including all unit, integration and Testcontainers tests.
2. Flyway V47–V52 against an empty database and a production-like upgraded database.
3. Kafka quarantine/replay with broker restart and acknowledgement failure simulation.
4. PostgreSQL maintenance, partition retention and legal-hold blocking on staging data.
5. Kubernetes canary rollout with real Prometheus metric queries and rollback.
6. Break-glass and four-eyes workflows through the real authentication, mTLS and HMAC chain.
7. Participant certification against each participant's approved UAT endpoint.

Production sign-off must not rely only on static validation.
