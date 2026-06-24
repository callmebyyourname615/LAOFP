# Phase 76 Operator Runbook

1. Merge Phase 74 and Phase 75 and ensure a clean working tree.
2. Run `scripts/execute-and-verify/15-phase76-operational-evidence.sh`.
3. On a protected UAT runner, set one shared `RUN_ID`, collect authoritative evidence, and normalize it.
4. Build and verify the evidence ledger.
5. Obtain unique commit-bound approvals from Engineering, QA, SRE, SecOps, Change Management and Business Operations.
6. Evaluate the decision policy. Do not override non-waivable controls.
7. Archive the final bundle in immutable object storage and sign externally with the approved organization key.
