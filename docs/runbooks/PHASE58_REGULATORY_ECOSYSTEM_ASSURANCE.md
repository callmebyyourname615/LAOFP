# Phase 58 Regulatory & Ecosystem Assurance Runbook

## Purpose
Phase 58 converts regulatory, participant, cryptographic, privacy, decision, messaging, settlement, simulation and supplier controls into release-bound evidence.

## Safety rules
- Run only on protected runners with an immutable release reference, full Git SHA and image digest.
- Never place raw PII, account numbers, keys or production payloads in snapshots.
- Every snapshot must carry the same release identity.
- A failed phase blocks Phase 58J. There is no risk-acceptance bypass for critical controls.
- Phase 58G may recommend `HOLD_NEW_OUTBOUND` but does not alter production traffic itself.
- Phase 58D assesses erasure eligibility and rights cases but never deletes records.
- Phase 58H must use sanitized/tokenized data and isolated networking.

## Execution order
`58A → 58B/58C/58D → 58E/58F → 58G → 58H; 58C+58D → 58I; all → 58J`

## Required identities
`ASSURANCE_ENVIRONMENT`, `RELEASE_REFERENCE`, `RELEASE_GIT_COMMIT`, and `RELEASE_IMAGE_DIGEST` are mandatory.

## Evidence handling
Store generated evidence in an immutable, versioned, KMS-encrypted object store with Object Lock COMPLIANCE. Verify the evidence manifest before supervisory sign-off.

## Abort criteria
Abort on identity mismatch, stale snapshot, missing report or message pack, unbalanced settlement, expired participant certification, prohibited cryptography, overdue privacy case, unapproved decision asset, production side effect in simulation, or expired critical-vendor assurance.
