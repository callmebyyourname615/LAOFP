# Phase 53A Delivery Notes — Repository Security Cleanup

## Scope

Phase 53A closes repository-side secret and sensitive-artifact exposure gaps before
production hardening continues. It deliberately excludes application business
logic, schema changes, external credential rotation execution, and production
infrastructure provisioning.

## Implemented

### Sensitive artifact removal

The apply script removes the exact paths in `PHASE_53A_DELETE_MANIFEST.txt`:

- `.env.bak`
- `app-live.log`
- `boot.log`
- `run-error.log`
- `new.txt`
- `backups/switching_db_legacy_20260521_101054.sql`

Deleted sensitive files are not included in the changed-files ZIP.

### Prevention controls

- expanded `.gitignore` for env files, logs, dumps, backup exports, keys,
  keystores, generated evidence, and OS metadata;
- expanded `.dockerignore` so the same material cannot enter Docker build context;
- removed hard-coded local password fallbacks from Docker Compose, DB bootstrap, Spring configuration, and archive property defaults;
- blank/safe committed environment templates;
- local random secret generator and validator with mode `0600`, key-strength checks, and no secret output;
- tracked-file and content policy scanner with redacted JSON reports;
- staged pre-commit scanner and installation script;
- Git full-history prohibited-path scanner;
- guarded `git-filter-repo` purge script that refuses normal working clones and
  never force-pushes;
- repository hygiene regression tests;
- CI security workflow enabled on PR, `main`, manual, weekly, and reusable calls;
- branch-protection script updated with mandatory security contexts;
- security policy, cleanup guide, history rewrite runbook, and rotation checklist.

## Files changed or added

### Root/configuration

- `.gitignore`
- `.dockerignore`
- `.gitleaks.toml`
- `.env.example`
- `.env.prod.example`
- `backup/.env.example`
- `docker-compose.yml`
- `docs/overall.md`
- `src/main/resources/application.yml`
- `src/main/java/com/example/switching/config/ArchiveProperties.java`
- `SECURITY.md`
- `PHASE_53A_DELETE_MANIFEST.txt`
- `apply-phase53a.sh`
- `PHASE_53A_DELIVERY_NOTES.md`
- `IMPLEMENTATION_PROGRESS.md`

### CI and developer controls

- `.github/workflows/security.yml`
- `.githooks/pre-commit`
- `scripts/configure_branch_protection.sh`
- `scripts/init-db-users.sh`
- `scripts/check_prod_config.sh`

### Security policy, scripts, and tests

- `security/policy/repository-hygiene.json`
- `security/policy/history-purge-paths.txt`
- `security/scripts/verify_repository_hygiene.py`
- `security/scripts/verify-repository-hygiene.sh`
- `security/scripts/generate-local-env.sh`
- `security/scripts/check-local-env.sh`
- `security/scripts/install-git-hooks.sh`
- `security/scripts/scan-git-history.sh`
- `security/scripts/purge-sensitive-history.sh`
- `security/tests/repository-hygiene-test.sh`
- `security/tests/generate-local-env-test.sh`

### Documentation

- `docs/security/CI_SECURITY_GATES.md`
- `docs/security/REPOSITORY_SECURITY_CLEANUP.md`
- `docs/security/GIT_HISTORY_PURGE_RUNBOOK.md`
- `docs/security/SECRET_ROTATION_CHECKLIST.md`

## Required operator actions after applying

1. Run `./apply-phase53a.sh`.
2. Review `git status --short` and ensure all six prohibited files are deleted.
3. Rotate and revoke every credential listed in the rotation checklist.
4. Classify the database dump and log exposure through the incident process.
5. Freeze repository writes and perform the mirror-clone Git history rewrite.
6. Require all security jobs through branch protection.
7. Delete/reclone every old clone and clear CI/source-archive caches.
8. Run full-history Gitleaks and repository hygiene checks.

## Validation status in the delivered overlay

The implementation is considered code-complete when local syntax and regression
checks pass. Production closure still requires external credential rotation,
history rewrite, remote branch protection, and approved evidence.

## Validation executed for this delivery

Passed:

- repository hygiene scan across all staged/tracked files;
- staged-file scanner;
- repository hygiene negative/positive regression cases;
- local environment generator test, including mode `0600` and ignored-file check;
- exact-value reuse scan for high-entropy credentials recovered from the removed
  env backup; no value remained in the current source tree;
- Bash syntax checks for all Phase 53A shell scripts and hooks;
- Python bytecode compilation;
- JSON, YAML, and TOML parse checks;
- `git diff --check`;
- idempotent second execution of `apply-phase53a.sh`;
- history-purge mirror dry run;
- history scanner correctly blocked the contaminated baseline and identified all
  six historical prohibited paths without printing values;
- `.env.prod.example` intentionally fails closed until placeholders are rendered.

Environment limitations:

- Docker Compose CLI was not available in the scan environment, so only YAML
  parsing and interpolation-source review were executed for `docker-compose.yml`.
- Maven compile could not run because the Maven wrapper distribution download
  from Maven Central was unavailable. No Java behavior was added; the Java change
  only removes two literal fallback values.
- Gitleaks CLI was not installed locally. The CI workflow remains configured to
  execute the full-history Gitleaks action after the mandatory history rewrite.
