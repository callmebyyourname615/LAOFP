# Switching Production Go-Live Master Runbook — Phase 55

## Purpose

This runbook controls the transition of one immutable Switching release candidate from certified artifacts to production operational acceptance. It never authorizes a deployment by itself. GitHub Environment approval, the release calendar gate, two-person decisions, and production access controls remain mandatory.

## Non-negotiable release identity

Every Phase 55 command must use the same values:

- `RELEASE_REFERENCE`
- `RELEASE_RC_ID`
- `RELEASE_GIT_COMMIT`
- `RELEASE_APP_IMAGE_DIGEST`
- `RELEASE_MIGRATION_IMAGE_DIGEST`

Changing any value requires a new release candidate and a restart from Phase 55A.

## Execution sequence

| Phase | Environment | Mutates production | Exit condition |
|---|---|---:|---|
| 55A | release | No | Signed immutable RC verified |
| 55B | production | Read-only | All dependency contracts pass |
| 55C | production-dry-run | Isolated only | V83 migration and reconciliation pass |
| 55D | production | Read-only | Baseline captured, hashed, archived |
| 55E | production | Yes, security controls | Access and network controls pass |
| 55F | production | No | Command center and cutover plan approved |
| 55G | production | Yes | Migration complete and 5% canary passes |
| 55H | production | Yes | 5→25→50→100% stages pass |
| 55I | hypercare | Read-only | Minimum hypercare exit criteria pass |
| 55J | hypercare | No | Business, security, and operations accept BAU |

## Required command pattern

```bash
export GOLIVE_ROOT=build/phase55-golive
export RELEASE_REFERENCE='CHG-...'
export RELEASE_RC_ID='switching-...'
export RELEASE_GIT_COMMIT='<40-char-sha>'
export RELEASE_APP_IMAGE_DIGEST='sha256:<64-hex>'
export RELEASE_MIGRATION_IMAGE_DIGEST='sha256:<64-hex>'

scripts/golive/run_phase55_golive.sh 55A
```

Each phase has additional required variables. Read the phase script and checklist before execution.

## Stop conditions

Stop promotion and invoke rollback when any condition occurs:

- Critical alert firing
- Transaction, settlement, journal, or outbox reconciliation failure
- Flyway version is not 83
- Migration Job fails or exceeds its deadline
- Error rate or P95/P99 latency exceeds the configured gate
- Kafka lag or outbox backlog grows beyond the threshold
- Database replication lag exceeds the threshold
- Signed decision is missing, stale, invalid, or references different evidence
- Release identity changes
- Production context or namespace cannot be positively identified

## Database rollback policy

V83 is forward-compatible and should use forward-fix rather than destructive schema rollback. Application rollback is permitted only when the previous application version is confirmed compatible with schema version 83. Never delete Flyway history or manually reverse a production migration during cutover.

## Evidence handling

- Evidence root: `build/phase55-golive`
- Do not edit evidence after creation.
- Every immutable package has SHA-256 inventory.
- Do not store passwords, tokens, kubeconfigs, private keys, raw payloads, account numbers, or PII in evidence.
- Phase 55J runs a final secret scan before operational acceptance.
