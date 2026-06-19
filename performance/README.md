# Phase 09 — Performance and capacity testing

Run only in an isolated performance environment against the exact immutable release-candidate digest. Do not target production.

## Scenarios

- `smoke`: low-volume environment validation.
- `sustained-2k-tps`: 2,000 inquiry requests/second for 300 seconds.
- `burst-10k-tps`: 10,000 inquiry requests/second for 60 seconds.
- `vpa-500-concurrent`: 500 concurrent VPA lookups.
- `qr-200-concurrent`: 200 concurrent payments against a pre-seeded active static QR. Set `QR_ID` and optionally `QR_AMOUNT`.
- `webhook-10k`: 10,000 webhook test events. Set `WEBHOOK_ID`.
- `soak-8h`: mixed inquiry/VPA traffic for 8 hours; override `DURATION=24h` for certification.
- `settlement`: seed and batch 500,000 DNS transfers using `performance/settlement/run_settlement_benchmark.sh`.

Example:

```bash
BASE_URL=https://performance.example \
API_KEY=... \
performance/scripts/run-k6.sh smoke
```

## Evidence and integrity

Do not approve capacity from k6 output alone. Run:

```bash
RELEASE_DIGEST=sha256:... performance/scripts/capture-capacity-evidence.sh
PERF_EXPECTED_TX_COUNT=500000 performance/scripts/reconcile-performance-data.sh
```

Attach CPU, memory, GC, Hikari, Kafka lag, DB locks/slow queries, outbox backlog, HPA scale-time, reconciliation results, and `CAPACITY_REPORT_TEMPLATE.md`. The report must identify the exact image digest and environment topology.
