# Phase 74 Exit Criteria

- Maven verify has zero failures and errors.
- All 99 migrations through V106 pass clean-install and upgrade tests.
- UAT is stable for at least 24 hours.
- Secret rotation and repository purge are signed.
- SMOS operators, TOTP, RBAC and maker-checker pass.
- Smoke, 2K, 10K, 20K and 8-hour soak pass.
- Settlement 500K is balanced and idempotent.
- RPO/RTO, failback, alerts and eight real chaos experiments pass.
- The signed UAT bundle is commit and digest matched.
