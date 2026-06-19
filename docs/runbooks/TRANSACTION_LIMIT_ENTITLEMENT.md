# Runbook — Transaction Limit and Entitlement Control

## Trigger
`switching_limits_denied_last15m` spikes, a participant reports an unexpected decline, or a limit override is requested.

## Triage
1. Locate the transaction in `transaction_limit_decision_audit`.
2. Confirm active entitlement for participant/product/channel/currency and its effective window.
3. List every matching active policy, not only the most specific policy.
4. Inspect hourly/daily consumption in the policy time zone.
5. Verify duplicate retries did not create a second transaction reference.

## Recovery
Correct policy data through the governed configuration workflow. For a time-critical exception, create a transaction-bound override with exact amount and short expiry; a different actor must approve it. Never edit consumption rows manually.

## Evidence
Capture policy IDs/versions, entitlement ID, decision hash, override request/approval/use timestamps, and post-recovery transaction result.
