# Phase 70 Exit Criteria

Phase 70 is code-complete only when all of the following are true:

- Static verifier returns `PASS`.
- Shell, Python, JSON and YAML syntax checks pass.
- Webhook configuration starts without a globally configured `ObjectMapper`.
- Cross-border rail timestamps bind with `Types.TIMESTAMP_WITH_TIMEZONE`.
- Participant buckets reject concurrent requests beyond configured capacity.
- Invalid policy reload retains the last good revision.
- Rejection responses include `429` and `Retry-After`, and no plaintext credential appears in identity/audit data.
- Promotion concurrent reservations cannot exceed the cap.
- Balanced and mismatched funder ledgers are distinguished correctly.
- `STRICT_PRIMARY` and `READ_YOUR_WRITES` never use a replica; stale `EVENTUAL` reads fall back to primary.
- Maven compile, targeted tests, full `mvn verify`, and `00-run-all.sh` pass in a dependency-enabled environment.
- Delivery archive contains only declared Phase 70 changed files and no Flyway migration.
