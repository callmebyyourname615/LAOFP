# Phase 70 Operator Runbook

## Preflight

```bash
./scripts/phase70/run_phase70.sh preflight
```

This validates file contracts and the participant policy without starting infrastructure.

## Full verification

```bash
./scripts/phase70/run_phase70.sh verify
```

The verify mode runs compile, targeted Phase 70 tests, full `mvn verify`, and `scripts/execute-and-verify/00-run-all.sh`. Evidence is stored under `evidence/phase70/<run-id>/`.

## Rate-limit rollout

1. Keep `RATE_LIMIT_ENABLED=true` in production.
2. Place the reviewed policy at `RATE_LIMIT_POLICY_FILE`.
3. Start with conservative participant overrides.
4. Confirm `X-RateLimit-*` and `Retry-After` behavior in UAT.
5. Watch `PARTICIPANT_RATE_LIMIT_EXCEEDED` audit events; never copy API-key values into policy or logs.
6. To change quota, atomically replace the policy file. Invalid updates are rejected and the prior policy remains active.

## Promotion reconciliation

Call:

```text
GET /api/operations/promotions/funder-ledger/reconciliation?funderParticipantId=BANK_A&currency=LAK
```

A `MISMATCH` status blocks promotion launch/cutover until reservation, consumption and settlement coverage variances are zero.
