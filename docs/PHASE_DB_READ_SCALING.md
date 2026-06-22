# Database Read Scaling and Reporting Separation

## Decision

Do not introduce sharding in this programme.  The current PostgreSQL primary,
physical read replica, daily partitions, and archive store are retained.  A
physical replica intentionally contains a complete copy of the HOT database;
it improves read capacity and recovery, not storage consumption.

## Phase 1 — Safety baseline

1. Keep all writes, payment state transitions, idempotency, settlement,
   reconciliation, ledger, and Flyway migrations on the primary.
2. Keep the primary as the default even for `readOnly` transactions. Route only
   explicitly injected reporting queries to the replica; payment reads can be
   correctness-sensitive.
3. Keep the primary as the automatic fallback when no replica endpoint is
   configured or a query requires strong consistency.
4. Protect read-after-write: unsafe HTTP methods and requests carrying
   `X-Consistency: strong` stay on the primary for their full request.

**Exit criteria:** replica credentials have `CONNECT`/`SELECT` only, dashboard
and reporting calls can tolerate replication lag, and no payment path is
routed to the replica.

## Phase 2 — Reporting separation

1. Serve the operations dashboard and other explicitly approved historical
   query paths through `reportingJdbcTemplate`.
2. Prefer `hourly_transaction_summary` and `daily_transaction_summary` over
   scans of the partitioned transaction tables.
3. Keep settlements and financial controls on the primary even when their
   endpoint is a GET, because their operational freshness is correctness
   sensitive.

**Exit criteria:** dashboard/report load is measurable on the replica and no
report query causes a write or locks the primary transaction tables.

## Phase 3 — Database design hardening

1. Treat `transaction_lookup` and `inquiry_lookup` as the globally unique
   reference directories; make their maintenance and reconciliation explicit.
2. Require a date range or a reference-directory lookup for cross-date search;
   prohibit unbounded scans of partitioned tables in operator APIs.
3. Review indexes from production query plans, archive expired partitions, and
   measure primary CPU, write IOPS, locks, replica lag, and report latency.

**Exit criteria:** quarterly capacity evidence shows partition pruning and
reporting isolation meet SLOs.

## Phase 4 — Scale decision gate

Consider sharding only when the primary remains saturated after the prior
phases.  The evidence must show sustained write/lock/maintenance pressure,
not replica data duplication.  If approved, shard transaction workloads only;
keep settlement and financial ledger centrally consistent until a separately
designed distributed-ledger programme is accepted.

## Rollout and rollback

Deploy with `SWITCHING_READ_REPLICA_ENABLED=false` first, verify the primary
fallback, then enable it after replica lag and permissions are validated.
Rollback is one configuration change: set the flag to `false`; all reads use
the primary without schema changes or data migration.
