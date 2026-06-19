# Delivery Notes

Implemented from baseline `main@185d5af` against `IMPLEMENTATION_GUIDE.md`:

- Sprint 1 / Item 1 — Kubernetes Flyway Migration Job
- Sprint 1 / Item 2 — Immutable Container Image
- Sprint 1 / Item 3 — Full CI Test Gate and Security Scans

See `IMPLEMENTATION_PROGRESS.md` and `docs/implementation/SPRINT1_PHASES_1_TO_3.md`.

Excluded from this delivery for safety/cleanliness:

- `.env`
- `backups/` database dump
- `.git/`
- `target/`
- log and macOS metadata files

Environment-dependent validation still required: GHCR push, cluster migration Job, staging rollout/rollback, and repository branch-protection activation.
