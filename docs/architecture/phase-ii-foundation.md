# Phase II Architecture Foundation

Phase II adds five bounded contexts without bypassing the Phase I payment, audit,
outbox, security, reconciliation, and settlement controls.

## Boundaries

- `rtp`: request lifecycle above the existing transfer rail.
- `promotion`: promotion eligibility, budget, application, and settlement.
- `paymentorchestration`: common push-payment lifecycle policy.
- `crossborder`: durable partner rail adapters and reconciliation.
- `reportdelivery`: scheduled generation and controlled delivery.

## Mandatory contracts

1. New features are disabled by default and enabled per environment.
2. A participant-scoped idempotency key is fingerprinted with SHA-256.
3. State transitions are persisted and invalid terminal-state mutations fail closed.
4. External side effects must use the existing outbox/retry controls.
5. Every new SHA-256 database column is `VARCHAR(64)`.
6. Production certification must verify migration predecessors V83 and V84 before V85.
7. No Phase II feature may duplicate transfer settlement or fee ledgers.

## Phase II-01–04 runtime scope

The first delivery creates RTP schema and APIs for create, query, and cancel. It
does not authorise, transfer, settle, expire, or emit external webhook events;
those are introduced in later Phase II deliveries.
