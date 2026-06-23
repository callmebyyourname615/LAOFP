# Phase 71 Exit Criteria

Phase 71 is complete only when:

1. Cross-border temporal binding uses explicit PostgreSQL SQL types and targeted tests pass.
2. `./mvnw clean verify` reports zero failures and zero errors with fresh reports.
3. All 99 migrations through V106 pass clean-install and supported upgrade certification.
4. UAT runs four application replicas and required stateful dependencies for at least 24 stable hours.
5. Six exposed credentials are rotated, old values disabled, and Git history/caches invalidated.
6. SMOS initial operators, TOTP, RBAC, participant isolation, sessions, and maker-checker pass runtime tests.
7. Phase 61/65/66/68/69/70 preflight chain passes against the same immutable images.
8. Performance, rate limiting, settlement 500K, backup, PITR, HA/DR, and alerts meet approved thresholds.
9. Evidence is SHA-256 inventoried and approved by Engineering, QA, Security, SRE, Product, and Change Management.
10. Phase 54 entry bundle decision is `GO` and P0 blocker count is zero.
