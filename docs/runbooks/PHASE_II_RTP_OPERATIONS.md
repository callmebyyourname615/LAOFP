# Phase II RTP Operations Runbook

## Monitors

- Pending RTP requests and expiry backlog.
- Installments in `FAILED` or `PROCESSING` for longer than the operational SLA.
- Transfer references duplicated across RTP requests or installments.
- RTP event delivery failures.

## Recovery

1. Confirm the RTP request and installment status from the database.
2. Confirm the transfer idempotency key `RTP-{requestId}-INST-{number}`.
3. Never manually create a second transfer for the same installment.
4. For a failed installment below the retry limit, allow the scheduler to retry.
5. For an accepted asynchronous transfer, use the settlement callback endpoint
   only after the transfer rail proves finality.
6. Reconcile `authorised_amount`, `settled_amount`, and installment totals.

## Abort conditions

- Settled amount exceeds authorised amount.
- Duplicate transaction reference.
- Payer/payee participant mismatch.
- Missing inquiry reference.
- Invalid state transition.
