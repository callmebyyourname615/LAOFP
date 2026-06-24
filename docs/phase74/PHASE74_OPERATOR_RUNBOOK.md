# Phase 74 Operator Runbook

1. Freeze the Git commit and application/migration image digests.
2. Run `scripts/phase74/run_phase74.sh --preflight`.
3. Close migration/source blockers before runtime execution.
4. Execute 74A–74E before load or destructive drills.
5. Execute 74F–74H with QA and financial reconciliation observers.
6. Execute 74I in an approved UAT disruption window.
7. Sign attestations only after reviewing original logs and metrics.
8. Run 74J to build the immutable UAT handoff bundle.
