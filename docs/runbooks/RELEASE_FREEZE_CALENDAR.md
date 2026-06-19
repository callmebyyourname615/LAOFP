# Release Freeze Calendar Runbook

## Purpose

Production deployment is fail-closed unless the release is inside an approved window, is not blocked by a hard freeze, and has a recent independently recorded `ALLOW` decision bound to immutable release evidence.

## Roles and separation of duties

- Requester prepares release evidence and change ticket.
- Evaluator reviews evidence and records ALLOW/DENY; evaluator must not be the sole code author or deploy executor.
- Deploy executor supplies the same release reference to the digest-pinned deployment workflow.
- Emergency/freeze exception approver must differ from the requester, as enforced by the database constraint.

## Standard release sequence

1. Build and verify the immutable image and release evidence.
2. Confirm `release_change_window` contains the approved production window and change type.
3. Confirm no active `HARD` freeze. During a freeze, create and independently approve a one-time exception.
4. Record the governance decision:

```bash
DB_URL="$RELEASE_GATE_DB_URL" \
RELEASE_REFERENCE="CHG-2026-0001" \
ENVIRONMENT="production" \
CHANGE_TYPE="STANDARD" \
DECISION="ALLOW" \
DECISION_REASON="CAB approved digest and evidence package" \
EVALUATED_BY="release-risk-approver" \
RELEASE_EVIDENCE_FILE="build/release-evidence.json" \
  scripts/release/record_gate_decision.sh
```

5. Start `.github/workflows/deploy.yml` within 15 minutes using the identical reference/change type.
6. The workflow runs `check_change_window.sh` before configuring or mutating the cluster.
7. After a successful rollout, any approved freeze exception is marked `USED` so it cannot be replayed.

## Gate denial reasons

- No currently open matching window for a STANDARD change.
- Active HARD freeze without an approved, unexpired exception for this release reference.
- Emergency release outside a window without an approved exception.
- Missing/stale/non-ALLOW decision.
- Decision evidence hash is absent or malformed.
- Database unavailable: the deployment remains denied.

## Emergency release

Emergency does not mean bypass. It requires incident/change reference, approved exception, ALLOW decision, immutable digest, migration evidence, rollback owner and post-implementation review.

## Verification and audit

Preserve `build/release-gate-evidence.json`, migration log, rendered manifests, deployed digest, rollout result and exception-consumption result. Reconcile these with `release_gate_decision` and GitHub environment approvals.
