# Runbook: Phase 55 Production Go-Live

Use `docs/golive/MASTER_GO_LIVE_RUNBOOK.md` as the master procedure and run each phase through `scripts/golive/run_phase55_golive.sh`.

Key controls:

- Immutable app and migration digests
- Phase 54 evidence prerequisite
- Production dependency probing
- Isolated migration dry run
- Financial and queue baseline/reconciliation
- Least-privilege and explicit network allowlists
- Signed, evidence-bound promotion decisions
- Automatic rollback on gate failure
- Hypercare exit criteria
- Three-party BAU acceptance

Never bypass a failed phase by editing `result.json`. A rerun must archive the prior attempt through `GOLIVE_RERUN_CONFIRMATION=I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT`.
