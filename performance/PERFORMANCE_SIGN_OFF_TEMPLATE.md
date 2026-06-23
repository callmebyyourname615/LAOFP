# Performance Certification Sign-Off

- Release commit:
- Application image digest:
- Migration image digest:
- UAT topology:
- Run ID:
- Test window (UTC):

| Scenario | Target | Actual TPS | P95 | Error rate | Dropped iterations | Result |
|---|---:|---:|---:|---:|---:|---|
| Smoke | 100 TPS / 5m | | | | | |
| Sustained 2K | 2,000 TPS / 30m | | | | | |
| Sustained 10K | 10,000 TPS / 60m | | | | | |
| Burst 20K | 20,000 TPS / 15m | | | | | |
| Soak | 5,000 TPS / 8h | | | | | |
| Settlement | 500,000 tx | | | | | |

- [ ] No DB connection exhaustion
- [ ] Kafka lag returned to baseline
- [ ] No memory leak; GC pause < 100 ms
- [ ] Reconciliation matched
- [ ] Capacity scaling thresholds recorded

Perf Lead: __________  Date (UTC): __________
Engineering Lead: __________  Date (UTC): __________
