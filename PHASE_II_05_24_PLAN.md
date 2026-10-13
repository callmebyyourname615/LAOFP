# Phase II 05-24 Plan

## Scope

Phase II 05-24 covers RTP authorisation and settlement, promotion management, push payment orchestration, cross-border rail journaling, and scheduled report delivery.

## Feature Flags

All Phase II runtime capabilities are default-off and must be enabled explicitly per environment:

- `PHASE_II_RTP_ENABLED`
- `PHASE_II_PROMOTION_ENABLED`
- `PHASE_II_PUSH_ORCHESTRATOR_ENABLED`
- `PHASE_II_CROSS_BORDER_ENABLED`
- `PHASE_II_REPORT_DELIVERY_ENABLED`

## Release Gates

- Run `python3 scripts/verify_phase_ii_05_24_static.py`.
- Run focused integration tests for RTP, promotion budget concurrency, push payment orchestration, rail journal idempotency, and report artifact idempotency.
- Attach operational runbook evidence for every enabled Phase II capability.

## Rollback

Disable the relevant feature flag first. For data-bearing flows, preserve journal, reservation, report artifact, and settlement records for reconciliation before replay or cleanup.
