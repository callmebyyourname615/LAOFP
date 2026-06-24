# Phase 80 Operator Runbook

1. Merge Phase 78 and 79, create an immutable release candidate and record the image digest.
2. Run `scripts/phase80/run_phase80.sh preflight`.
3. Configure a protected UAT environment and required secrets/connection variables.
4. Execute destructive controls only with the corresponding `PHASE80_ALLOW_*` confirmation.
5. Obtain independent attestations from QA, SRE, SecOps and Change Management.
6. Run `scripts/phase80/run_phase80.sh full` using one `PHASE80_RUN_ID`.
7. Verify `FINAL_DECISION.json`, `SHA256SUMS` and release/image identity.

No secret value belongs in logs or attestations. Financial mismatch, transaction loss, duplicate posting and restore failure are non-waivable.
