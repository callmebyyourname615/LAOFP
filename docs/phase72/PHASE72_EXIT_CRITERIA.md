# Phase 72 Exit Criteria

A GO decision requires all of the following:

- Phase 71 is merged and no Phase 71-owned file is modified by Phase 72.
- Cross-border source contains no untyped `Instant` JDBC `setObject` call.
- Maven clean verify executes tests and reports zero failures and zero errors.
- The repository execute-and-verify gate passes.
- UAT dependencies are reachable and primary/replica roles are correct.
- Smoke, 2K, 10K, 20K, soak and settlement 500K evidence all pass strict thresholds.
- Backup verification, isolated restore, PITR and every required DR scenario pass.
- RPO is under 5 minutes, RTO is under 30 minutes, and financial loss/mismatch is zero.
- Secret rotation/history purge and runtime security/alert attestations are signed.
- Final attestation is non-synthetic and matches the exact Git commit.

Any missing runtime step prevents GO.
