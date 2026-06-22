# Phase II Promotion Operations Runbook

## Activation

1. Create a promotion in `DRAFT`.
2. Validate rule evidence, time window, funder, currency, budget and discount.
3. A different operator activates the promotion.
4. Confirm audit events `promotion.created` and `promotion.activated`.

## Budget incident

- Stop new promotion activation if reserved plus consumed budget approaches cap.
- Do not edit budget counters directly.
- Release only applications still in `RESERVED` state.
- A consumed application must have exactly one promotion settlement.
- Reconcile funder debit, beneficiary credit and promotion application amount.

Concurrent reservations are guarded by a locked conditional update and cannot
make the available budget negative.
