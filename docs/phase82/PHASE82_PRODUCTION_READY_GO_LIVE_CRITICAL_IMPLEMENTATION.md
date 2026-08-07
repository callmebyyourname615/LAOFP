# Phase 82 Production-Ready Go-Live Critical Implementation

## Purpose

Phase 82 extracts only the critical go-live blockers from the latest production readiness review. This phase must be executed before any release candidate is promoted to production. Lower-priority enhancements are out of scope until every P0/P1 gate below is closed with runtime evidence.

## Priority Order

| Priority | Phase | Objective | Exit Gate |
|---|---|---|---|
| P0 | 82A | Restore test baseline | `./mvnw clean test` exits with 0 failures and 0 errors |
| P0 | 82B | Secure distributed request controls | OAuth revoke, rate limit, and anti-replay work across all pods |
| P0 | 82C | Close high-risk endpoint evidence | All 17 high-risk endpoints have auth, audit, validation, and evidence |
| P0 | 82D | Remove secret exposure risk | Secrets rotated, history cleaned, and scans show no active leak |
| P1 | 82E | Prove load and settlement capacity | PERF-10K, BURST-20K, soak, and Settlement-500K pass |
| P1 | 82F | Prove recoverability | PITR and regional DR failover/failback executed successfully |
| P1 | 82G | Complete security assurance | SAST, SCA, container scan, and penetration test issues triaged |
| P1 | 82H | Produce immutable release | Signed release includes commit, image digest, SBOM, and attestations |
| P1 | 82I | Obtain go-live sign-off | Security, QA, SRE, Operations, and Business approve |

## 82A Maven Test Baseline

Run the full Maven test suite first. No production readiness work may be considered complete while the baseline is red.

Commands:

```bash
./mvnw clean test
./mvnw verify
```

Implementation notes:

- Fix failing tests before adding new functionality.
- Keep Testcontainers and Flyway failures visible; do not skip integration tests for release evidence.
- Attach Surefire/Failsafe reports and JaCoCo output to runtime evidence.

Exit evidence:

- `target/surefire-reports`
- `target/site/jacoco`
- Console log proving 0 failures and 0 errors

## 82B Distributed OAuth Revoke, Rate Limit, And Nonce Anti-Replay

Replace any node-local security state with shared/distributed state.

Implementation scope:

- Store revoked OAuth token identifiers in PostgreSQL or Redis with TTL.
- Check token revocation on every protected request.
- Move rate-limit counters from JVM-local memory to Redis, database-backed buckets, or an ingress/API gateway policy.
- Add nonce + timestamp anti-replay validation for sensitive write endpoints.
- Persist used nonces in a shared store with short expiry.
- Reject stale timestamps, duplicate nonces, and invalid signatures.

Exit evidence:

- Multi-pod test proving revoke works after traffic shifts to another pod
- Multi-pod rate-limit test proving limits are global, not per instance
- Replay test proving duplicate nonce requests are rejected

## 82C High-Risk Endpoint Closure

Create a 17-endpoint closure matrix before coding. Each endpoint must have an owner, risk reason, control gap, fix, test, and evidence path.

Required controls:

- Authentication and authorization
- Input validation
- Audit logging
- Idempotency or duplicate protection where money movement is possible
- Rate limit or abuse control
- Evidence capture for successful and rejected flows

Exit evidence:

- Updated endpoint matrix
- Automated tests for each fixed endpoint
- Runtime request/response evidence with sensitive data redacted

## 82D Secret Rotation And Git History Cleanup

Complete secret hygiene before creating any release candidate.

Required actions:

- Rotate production and CI credentials.
- Store new values only in the approved secret manager.
- Run repository secret scanning after rotation.
- Clean Git history if any real secret was committed.
- Invalidate old CI caches, tokens, and deploy credentials.

Exit evidence:

- Signed secret rotation checklist
- Scan output with no active high-risk findings
- Confirmation that revoked credentials no longer authenticate

## 82E Performance And Settlement Proof

Run the required production-like benchmarks only after 82A through 82D are complete.

Required scenarios:

- PERF-10K sustained load
- BURST-20K burst load
- Soak test
- Settlement-500K batch/volume test

Exit evidence:

- k6 or benchmark raw results
- Grafana snapshots
- Database metrics such as `pg_stat_statements`
- Pod CPU/memory metrics
- Kafka lag or queue depth metrics where applicable
- Signed performance report

## 82F PITR And Regional DR Drill

Prove recovery with real restore and traffic movement, not documentation only.

Required drills:

- Full backup verification
- Point-in-time restore to a new database instance
- Application validation against restored data
- Regional failover
- Regional failback

Exit evidence:

- Backup artifact checksums
- Restore timestamp and target recovery point
- RTO/RPO result
- Data integrity checks
- Signed DR report

## 82G Security Assurance

Run all mandatory security checks against the release candidate.

Required checks:

- SAST
- SCA/dependency scan
- Container image scan
- Secret scan
- Penetration test

Exit gate:

- No open critical findings
- No open high findings without signed risk acceptance
- All accepted risks have expiry date, owner, and compensating control

## 82H Signed Immutable Release

Build one release artifact set and do not mutate it after approval.

Required contents:

- Git commit SHA
- Container image digest
- SBOM
- Build logs
- Test logs
- Security scan outputs
- Performance and DR evidence
- Deployment manifest or Helm/Kustomize reference
- Signature/attestation

Exit evidence:

- Signed release bundle
- Digest verification command output
- Immutable storage location

## 82I Go-Live Sign-Off

Go-live approval requires all prior gates to be closed.

Required approvers:

- Security
- QA
- SRE
- Operations
- Business owner

Exit evidence:

- Completed sign-off record
- Linked release bundle
- Linked rollback plan
- Linked hypercare/on-call plan

## Critical Execution Rule

Execute in order. Do not start performance, DR, release signing, or sign-off work until tests, distributed security controls, endpoint evidence, and secret hygiene are complete.
