# Switching Capacity Certification

- Release image digest:
- Commit SHA:
- Test environment / cluster:
- Test window (UTC):
- Database size / instance class:
- Kafka/Redpanda topology:
- Application replicas and resources:

## Results

| Scenario | Target | Actual throughput | P95 | P99 | Error rate | Dropped iterations | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| Sustained | 2,000 TPS / 300s | | | | | | |
| Burst | 10,000 TPS / 60s | | | | | | |
| VPA | 500 concurrent | | | | | | |
| QR | 200 concurrent | | | | | | |
| Settlement | 500,000 tx/cycle | | | | | | |
| Soak | 8–24h | | | | | | |

## Resource envelope

Document CPU, memory, DB connections, Kafka lag, GC pause, HPA behavior, outbox backlog, and storage IOPS at steady state and peak.

## Data integrity

Attach the reconciliation JSON, duplicate-reference check, outbox result, settlement item count, and audit-chain verification.

## Capacity decision

- Recommended minimum replicas:
- Maximum certified TPS:
- Headroom percentage:
- Scaling trigger:
- Known bottlenecks:
- Tuning changes:
- Engineering approver / date:
- Operations approver / date:
