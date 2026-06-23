# Phase 63D Performance Execution Report

Record the immutable application/migration image digests, UAT topology, test start/end timestamps and the paths to Phase 61G evidence.

| Scenario | Minimum observed rate | Threshold | Result | Evidence |
|---|---:|---|---|---|
| Smoke | 95 TPS | p95 < 200 ms; 0% failures |  |  |
| Sustained 2K | 1,900 TPS | p95 < 300 ms; failures < 0.1% |  |  |
| Sustained 10K | 9,500 TPS | p95 < 500 ms; failures < 0.1% |  |  |
| Burst 20K | 18,000 TPS average | p95 < 750 ms; failures < 0.5% |  |  |
| Soak 8h | 4,750 TPS | no leak or unbounded lag |  |  |
