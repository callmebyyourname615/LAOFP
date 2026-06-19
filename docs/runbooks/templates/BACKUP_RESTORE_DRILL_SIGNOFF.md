# Backup / PITR / Failover Drill Sign-Off

## Identification

| Field | Value |
|---|---|
| Environment | |
| Drill type | Base restore / PITR / DB failover / regional recovery |
| Change or incident ticket | |
| Backup image digest | |
| Application image digest | |
| Source database system identifier | |
| Backup ID | |
| Recovery target timestamp (UTC) | |
| Drill operator | |
| Observer / approver | |

## Timing and objectives

| Measurement | Target | Actual | Pass |
|---|---:|---:|---|
| Base-backup age at drill start | ≤ 30 hours | | |
| WAL archive age at drill start | ≤ 15 minutes | | |
| RPO | ≤ approved target | | |
| RTO | ≤ 60 minutes or approved target | | |
| Database failover time | ≤ approved target | | |

## Integrity evidence

- [ ] Encrypted archive checksum matched.
- [ ] `pg_verifybackup` passed.
- [ ] Required WAL chain was complete.
- [ ] Restore used an isolated target/PVC.
- [ ] PostgreSQL reached the expected recovery target and promoted.
- [ ] Flyway history was present and successful.
- [ ] Required tables and partitions were readable.
- [ ] Transaction and outbox reconciliation completed.
- [ ] Latest expected transaction/marker was present.
- [ ] Primary and secondary object-storage copies were verified.
- [ ] No plaintext database or backup secret appeared in logs/artifacts.

## Evidence locations

| Evidence | Location / checksum |
|---|---|
| Job or command logs | |
| Restore evidence JSON | |
| Backup metadata JSON | |
| Backup SHA-256 | |
| WAL evidence | |
| Prometheus/Grafana screenshots | |
| Failover marker evidence | |

## Exceptions and follow-up

| Finding | Severity | Owner | Due date | Ticket |
|---|---|---|---|---|
| | | | | |

## Decision

- [ ] PASS — recovery objectives and integrity checks met.
- [ ] CONDITIONAL PASS — approved exception recorded above.
- [ ] FAIL — production go-live gate remains closed.

| Role | Name | Signature / approval reference | Date |
|---|---|---|---|
| DBA | | | |
| SRE / Operations | | | |
| Application owner | | | |
| Information security | | | |
| Business continuity owner | | | |
