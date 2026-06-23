# Disaster Recovery Drill Sign-Off

- Release/image digest:
- Environment:
- Drill window (UTC):
- Evidence manifest SHA-256:

| Scenario | Actual RTO | Actual RPO | Transaction loss | Duplicate replay | Result |
|---|---:|---:|---:|---:|---|
| Application pod kill | | | | | |
| Kafka broker failure | | | | | |
| Network partition | | | | | |
| Object storage outage | | | | | |
| External API timeout | | | | | |
| Region failover (if required) | | | | | |

- [ ] Health returned to UP
- [ ] Financial counts/checksums reconciled
- [ ] No new duplicate transaction references
- [ ] Runbooks updated for discovered edge cases

SRE Lead: __________  Date (UTC): __________
Operations Lead: __________  Date (UTC): __________
