# Phase 81 Operator Runbook

1. Provision dashboard permissions before enabling `switching.phase81.dashboards.enabled`.
2. Smoke-test all four APIs and verify `Cache-Control: no-store` and RBAC denial paths.
3. Run promotion UAT, quota calibration and partition trigger evaluation from Phase 80 evidence.
4. Store paper runbooks and verify off-site Vault backup access.
5. Supply a Phase 80 `GO_PRODUCTION_CANARY` decision.
6. Enable `switching.phase81.bau.enabled` only after the hypercare roster and all required jobs are active.
