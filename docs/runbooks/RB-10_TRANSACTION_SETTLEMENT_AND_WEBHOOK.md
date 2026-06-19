# RB-10 — Transaction, Settlement and Webhook Operations

## SwitchingTransactionRejectSpike

Confirm whether rejects are business-valid or caused by infrastructure/configuration. Break down by reason code, participant, route, currency and release digest. Check sanctions freshness, limits, signing, mTLS, account lookup and connector health. Never bypass compliance controls to reduce the count.

Close after reject volume returns to baseline, affected participants are informed, and sample transactions are reconciled end-to-end.

## SwitchingSettlementFailure

1. Freeze further settlement execution for the affected business date/cycle.
2. Capture cycle, items, net positions, instructions, ledger journals, locks and audit entries.
3. Compare totals using independent reconciliation queries.
4. Determine whether the failure occurred before or after external RTGS submission.
5. Never rerun a cycle until idempotency and external instruction state are proven.

Escalate immediately to settlement operations and finance control. Any imbalance is a production stop condition. Recovery requires four-eyes approval and evidence that debit/credit totals match.

## SwitchingWebhookDeliveryFailure

Check final failures and overdue pending rows by participant and endpoint. Validate DNS/allowlist/proxy/TLS without printing signing secrets. Confirm endpoint registration has encrypted secrets and active key version.

Recover the dependency, then replay through the supported retry service. Preserve event ID and signature version. Do not create a new business event to compensate for a delivery problem.

## Evidence

Record transaction/cycle/event references using masked identifiers, timeline, dependency state, replay or rollback action, duplicate check, reconciliation result, approvers and alert resolution time.
