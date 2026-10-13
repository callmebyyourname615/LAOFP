# Phase II Promotion Operations Runbook

## Checks

- Confirm `PHASE_II_PROMOTION_ENABLED=true` only after promotion policy approval.
- Validate promotion rule packages before activation.
- Monitor promotion budget remaining and reservation expiry backlog.

## Reservation Lifecycle

- `reserve` creates a bounded reservation.
- `consume` moves reserved budget to consumed budget and writes funder ledger debit.
- `release` frees unused reserved budget.
- `refund` writes funder ledger credit.

## Failure Handling

- Budget reservations must not exceed cap.
- Expired reservations are released by the cleanup scheduler.
- Funder ledger reconciliation must be reviewed after failed or retried settlement.
