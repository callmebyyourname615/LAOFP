# POST /api/outbox-events/{id}/retry + POST /api/operations/outbox-events/{id}/mark-reviewed

Both PASS on UAT with SYSTEM_ADMIN (ROLE_ADMIN/ROLE_OPS) token.

## Bug found while sourcing test fixtures (not part of endpoint-coverage scope, flagged separately)

outboxEventId 62 (transferRef TRX-20260716043110-B32607EF) has been stuck FAILED since
2026-07-16T04:31:10 with lastError:

  could not execute statement [ERROR: value too long for type character varying(100)]
  [update transactions set accepted_at=?,amount=?,...]

This is a real data-truncation bug (some field being written back to `transactions` exceeds a
varchar(100) column), not a transient connector failure. Retrying this event would fail
identically every time since the root cause is unrelated to connector availability — that's why
it was routed to mark-reviewed instead of retry. Needs its own investigation to find which
column/value overflows.
