# Production Go-Live Sign-Off

## Release identity

| Field | Value |
|---|---|
| Release/change reference | |
| Git commit (40 characters) | |
| Container image reference | |
| Container digest (`sha256:`) | |
| Flyway version | 83 |
| Planned production window | |
| Rollback digest | |

## Mandatory evidence

| Evidence | Artifact/location | SHA-256 or immutable ID | Owner | Result |
|---|---|---|---|---|
| Repository security closure / secret rotation | | | Security | |
| Full Maven verification | | | Engineering | |
| V83 migration and runtime isolation | | | Database | |
| Production environment contract | | | Platform | |
| Vulnerability scans (Gitleaks/OWASP/Trivy/CodeQL) | | | Security | |
| Sustained 2k TPS | | | Performance | |
| Burst 10k TPS | | | Performance | |
| VPA/QR/Webhook concurrency | | | Performance | |
| Settlement 500k | | | Settlement | |
| Soak 8h or longer | | | SRE | |
| Backup and restore drill | | | Database | |
| DR suite | | | SRE | |
| Sanctions sync/freshness | | | AML | |
| Vault Transit rotation | | | Security | |
| Alert delivery drill | | | Observability | |
| Release calendar/freeze decision | | | Change Manager | |
| Runtime `manifest.json` verified with `--require-go-live-ready` | | | Release Manager | |

## Acceptance confirmation

- [ ] The release commit and image digest match every evidence bundle.
- [ ] Every mandatory runtime control is `PASS`; none is `FAIL` or `NOT_RUN`.
- [ ] No unresolved HIGH/CRITICAL vulnerability exists without an approved, time-bounded exception.
- [ ] Database upgrade and rollback/forward-fix procedures were rehearsed.
- [ ] Monitoring dashboards show real data and alert delivery reaches the approved receiver.
- [ ] Backup restore and DR recovery meet approved RPO/RTO.
- [ ] Production environment contract passes against the rendered secret/config delivery.
- [ ] Change window is open and no unapproved HARD freeze applies.
- [ ] Rollback owner, command, digest and decision threshold are confirmed.

## Decision

- [ ] GO
- [ ] CONDITIONAL GO — attach approved exceptions with owners and expiry
- [ ] NO-GO

Decision rationale:

```text

```

## Approvals

| Role | Name | Decision | Timestamp (UTC) | Signature/reference |
|---|---|---|---|---|
| Engineering Lead | | | | |
| Database Owner | | | | |
| Security | | | | |
| SRE/Platform | | | | |
| AML/Compliance | | | | |
| Business/Operations | | | | |
| Change/Release Manager | | | | |

## Post-deployment verification

- [ ] Migration Job completed at V83.
- [ ] Deployed digest matches approved digest.
- [ ] Readiness/liveness and critical synthetic transactions pass.
- [ ] Operational metrics and dashboards are populated.
- [ ] Error rate, latency, outbox, Kafka, DB pool and sanctions freshness are normal.
- [ ] One-time freeze exception, when used, is marked consumed.
- [ ] Evidence and final decision are archived immutably.
