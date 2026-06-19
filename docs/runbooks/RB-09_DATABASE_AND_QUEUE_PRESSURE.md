# RB-09 — Database Pool and Queue Pressure

## SwitchingDatabasePoolSaturated

### Meaning

Hikari has pending borrowers or active connections exceed 90% of pool capacity for five minutes.

### Immediate actions

1. Freeze deployments and non-essential batch jobs.
2. Capture Hikari metrics, PostgreSQL sessions, wait events, locks, and top query durations.
3. Confirm the database itself is healthy before increasing pool size; multiplying pool sizes across replicas can worsen exhaustion.

```sql
SELECT pid, usename, application_name, state, wait_event_type, wait_event,
       now() - query_start AS age, left(query, 300) AS query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start;
```

### Recovery

Terminate only sessions approved by the database operator. Roll back a release that introduced query or transaction regression. Scale within the capacity policy only when database headroom is proven. Confirm no long-running transaction, replication lag, or reconciliation mismatch remains.

## SwitchingOutboxBacklog

### Meaning

More than 100 outbox rows remain pending/processing for five minutes.

### Diagnose

Check worker health, Kafka producer/consumer state, connector failures, rows stuck in PROCESSING, retry schedule, and dead-letter counts. Compare database outbox state with Kafka offsets before replaying.

### Recovery

- Restore the failed dependency first.
- Use the approved outbox recovery operation for stale PROCESSING rows.
- Replay only by immutable event identifier and preserve idempotency keys.
- Do not bulk-update statuses manually.

### Integrity verification

Verify committed transactions have exactly the expected event set, consumers did not create duplicates, and pending count is monotonically decreasing. Capture before/after counts and replay references.

## Escalation

Page database/on-call immediately for connection exhaustion, lock chains blocking payment writes, or backlog growth with no worker progress. Declare an incident if either alert persists for ten minutes.
