# Phase 71 Operator Runbook

1. Freeze the candidate commit and record both application and migration image digests.
2. Run preflight and resolve every `BLOCKED` result before UAT execution.
3. Execute 71A–71G before load or destructive recovery drills.
4. Run 71H only in an isolated UAT load window.
5. Run 71I only after SRE confirms rollback and communications readiness.
6. Complete signed attestations from immutable evidence; never edit generated result files.
7. Run 71J to build the Phase 54 entry bundle.
8. Verify the bundle SHA-256 before handoff.

Abort immediately for financial mismatch, unknown image digest, stale test evidence, transaction loss, or an unapproved operator action.
