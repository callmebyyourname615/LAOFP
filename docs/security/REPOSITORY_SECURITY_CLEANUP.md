# Repository Security Cleanup — Phase 53A

## Objective

Phase 53A removes known sensitive artifacts from the working tree, prevents new
secret/data artifacts from entering Git or Docker build contexts, and provides a
controlled process for rotating credentials and rewriting contaminated history.
It does not rotate external credentials automatically and does not force-push a
rewritten repository.

## Known prohibited artifacts

The baseline repository contained the paths listed in
`PHASE_53A_DELETE_MANIFEST.txt`:

- a backup environment file containing credential material;
- application/runtime log files;
- a generated repository inventory text file;
- a legacy PostgreSQL dump containing application data.

The deleted files are intentionally not included in the Phase 53A changed-files
package. The apply script removes them by exact relative path.

## Apply the changed-files overlay

From the Switching repository root:

```bash
unzip Switching_Phase53A_Changed_Files.zip -d /tmp/phase53a
rsync -a /tmp/phase53a/Switching_Phase53A_Changed_Files/ ./
./apply-phase53a.sh
```

Review the staged deletions and modifications:

```bash
git status --short
git diff --stat
git diff -- .gitignore .dockerignore docker-compose.yml
```

Do not commit until the deleted paths, generated environment behavior, and CI
policy changes have been reviewed by the security/release owner.

## Controls implemented

### 1. Tracked-file policy

`security/scripts/verify-repository-hygiene.sh` scans every tracked file and
fails on:

- `.env` variants outside the approved templates;
- logs, dumps, backup exports, private-key/keystore formats, and macOS metadata;
- SQL files outside approved source directories;
- private-key headers and selected high-confidence provider tokens;
- credential-bearing URIs;
- literal secret assignments such as `DB_PASSWORD=<real value>`.

The scanner reports only path, line number, and rule ID. It never prints the
matching secret value. A redacted JSON report can be produced with:

```bash
security/scripts/verify-repository-hygiene.sh \
  --json-report build/security-history/repository-hygiene.json
```

### 2. Pre-commit protection

Install the repository-managed hook once per clone:

```bash
security/scripts/install-git-hooks.sh
```

The hook scans staged additions and modifications. It is a developer safeguard,
not a substitute for CI or branch protection.

### 3. CI enforcement

`.github/workflows/security.yml` now runs on pull requests, pushes to `main`,
manual execution, weekly schedule, and reusable workflow calls. Required jobs:

- Repository Hygiene;
- Security Policy Static Checks;
- Gitleaks Secret Scan;
- OWASP Dependency Check;
- Trivy Repository Scan;
- Trivy Container Scan;
- CodeQL SAST.

Use `scripts/configure_branch_protection.sh` to require these checks on `main`.

### 4. Secure local environment generation

Committed examples contain no usable passwords or keys. Generate local-only
values without printing them:

```bash
security/scripts/generate-local-env.sh
security/scripts/check-local-env.sh .env
```

Properties:

- cryptographically random values generated from the operating-system CSPRNG;
- output mode `0600`;
- validation of permissions, required keys, minimum length, password separation,
  and 32-byte Base64 key material;
- no secret values written to stdout/stderr;
- refuses to overwrite an existing `.env` unless `--force` is supplied;
- `.env` is ignored by Git and excluded from Docker build context.

`docker-compose.yml`, `scripts/init-db-users.sh`, `application.yml`, and `ArchiveProperties` no longer carry fallback passwords. Missing required
local secrets fail fast with an instruction to run the generator.

### 5. Git history controls

Deleting a file from the latest commit does not remove it from prior commits.
Run the mirror-clone procedure in `GIT_HISTORY_PURGE_RUNBOOK.md`. The history
scanner must pass before reopening normal development:

```bash
security/scripts/scan-git-history.sh
```

### 6. Credential rotation

All credential material found in a committed file is considered compromised.
Complete `SECRET_ROTATION_CHECKLIST.md`, revoke old values, and attach only
redacted evidence to the incident/change record.

## Mandatory validation

```bash
# Working-tree and policy regression checks
security/scripts/verify-repository-hygiene.sh
security/tests/repository-hygiene-test.sh
security/tests/generate-local-env-test.sh

# Syntax/static validation
bash -n apply-phase53a.sh security/scripts/*.sh security/tests/*.sh .githooks/pre-commit
python3 -m py_compile security/scripts/verify_repository_hygiene.py
python3 -m json.tool security/policy/repository-hygiene.json >/dev/null

# After the mirror history rewrite and credential rotation
gitleaks git --redact --report-format json \
  --report-path build/security-history/gitleaks-history.json .
security/scripts/scan-git-history.sh
```

## Acceptance criteria

Phase 53A is complete only when all conditions are true:

- prohibited working-tree files are deleted and not present in `git ls-files`;
- no prohibited path remains in any reachable branch or tag;
- Gitleaks full-history scan is clean or every exception is approved and scoped;
- exposed credentials/keys are rotated and old values revoked;
- database-dump exposure is classified and handled under the incident process;
- generated `.env` is mode `0600`, ignored by Git, and excluded from Docker;
- all repository-security jobs are required by branch protection;
- redacted evidence and approvals are stored outside the source repository.

## Rollback

Do not restore prohibited artifacts to Git. If a legitimate development fixture
is needed, create a synthetic, minimal dataset with no production identifiers or
credentials and place it in an approved test-resource directory. Any rollback of
the Git history rewrite must be performed from the protected pre-rewrite mirror,
with security approval, and must not restore exposed credentials for active use.
