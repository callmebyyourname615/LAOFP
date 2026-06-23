# Read Replica Routing and Consistency Policy

## Routing contract

- The primary datasource is the default and handles every write transaction.
- A Spring transaction marked `readOnly=true` is routed to the PostgreSQL replica.
- `ReadReplicaRoutingContext.forcePrimary(...)` overrides a read-only route for
  correctness-sensitive read-after-write operations.
- Physical connection acquisition is delayed by `LazyConnectionDataSourceProxy`
  until Spring has applied the transaction attributes.
- The application fails startup when replica routing is enabled without a URL or
  username. It does not silently send a configured replica workload to primary.

## Primary-only operations

The following operations must never use a potentially stale replica:

- idempotency decisions and duplicate detection;
- balance, liquidity and limit checks;
- payment state changes and immediate post-write inquiry;
- settlement cycle transitions and maker-checker execution;
- authentication, token/session revocation and role changes;
- promotion budget reservation, consumption and refund.

## Replica-eligible operations

Historical reports and scheme-wide dashboards may use replica-backed read-only
transactions. Responses must expose a freshness timestamp and use `no-store`.
Participant-scoped dashboards are denied until a separately reviewed row-level
scope contract is implemented.

## Availability behavior

Production does not use an implicit fallback from replica to primary. A failed
replica pool must make replica-eligible operations fail fast and alert, preventing
an unnoticed reporting load surge on the payment primary. Operators may explicitly
disable replica routing through an approved configuration change.

## UAT certification

Run the following with credentials supplied through `.pgpass`, Vault-injected
variables, or another process-safe secret mechanism:

```bash
TARGET_ENVIRONMENT=uat \
PRIMARY_DB_PSQL_DSN='host=... dbname=... user=...' \
READ_REPLICA_PSQL_DSN='host=... dbname=... user=...' \
scripts/phase62/certify_read_replica_uat.sh
```

The primary must return `pg_is_in_recovery() = false`; the replica must return
`true`. Capture replay lag and connection-level `transaction_read_only` settings
in the Phase 62 evidence directory.
