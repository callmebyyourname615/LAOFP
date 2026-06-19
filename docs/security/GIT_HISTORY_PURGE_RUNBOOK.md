# Git History Purge Runbook

## Purpose

Remove the exact prohibited paths in `security/policy/history-purge-paths.txt`
from every reachable commit and tag. This operation changes commit IDs and
requires coordinated replacement of all clones.

## Safety rules

1. Rotate/revoke exposed credentials before treating the repository as safe.
2. Freeze merges and pushes during the rewrite window.
3. Preserve a protected, access-controlled pre-rewrite mirror for incident and
   legal evidence. Never distribute it to developers.
4. Perform the rewrite in a fresh `--mirror` clone, never in a developer clone.
5. Do not add secret values to replacement-text files, shell history, tickets,
   screenshots, chat, or CI logs.
6. The provided script never force-pushes. A human release/security owner must
   review scan evidence before the remote is replaced.

## Prerequisites

- approved incident/change ticket;
- credential rotation owners assigned;
- repository push freeze announced;
- `git-filter-repo` installed from an approved package source;
- Gitleaks installed;
- protected backup location outside the source repository;
- permission to force-update all branches and tags.

## Step 1 — Freeze and record

Record without secret values:

```bash
git remote -v
git show-ref --head | sha256sum
git rev-list --all --count
```

Export branch/tag protection settings and identify integrations that cache commit
SHAs, including deployment manifests, release evidence, submodules, and build
allowlists.

## Step 2 — Create protected mirrors

```bash
git clone --mirror <repository-url> switching-pre-rewrite.git
cp -a switching-pre-rewrite.git switching-rewrite.git
chmod -R go-rwx switching-pre-rewrite.git switching-rewrite.git
```

Store `switching-pre-rewrite.git` in the approved evidence location. Restrict its
access because it still contains exposed data.

## Step 3 — Copy the purge tooling

The rewrite mirror has no working tree. Copy these reviewed Phase 53A files to a
separate operator directory:

```text
security/scripts/purge-sensitive-history.sh
security/policy/history-purge-paths.txt
security/scripts/scan-git-history.sh
.gitleaks.toml
```

Review the purge list. It must contain paths only, never credential values.

## Step 4 — Dry run

```bash
/path/to/purge-sensitive-history.sh \
  --repo "$PWD/switching-rewrite.git" \
  --output-dir "$PWD/purge-evidence"
```

Confirm the repository, current HEAD, and exact path count.

## Step 5 — Execute locally

```bash
/path/to/purge-sensitive-history.sh \
  --execute \
  --acknowledge-credential-rotation \
  --repo "$PWD/switching-rewrite.git" \
  --output-dir "$PWD/purge-evidence"
```

The script:

- refuses a non-bare repository;
- uses `git-filter-repo --invert-paths`;
- expires reflogs and prunes unreachable objects;
- writes only non-secret metadata to a mode-restricted evidence directory;
- does not push.

## Step 6 — Validate the rewritten mirror

Create a disposable working clone from the rewritten mirror:

```bash
git clone switching-rewrite.git switching-rewrite-validation
cd switching-rewrite-validation
security/scripts/verify-repository-hygiene.sh
security/scripts/scan-git-history.sh
```

Also verify every branch and tag expected before the rewrite still exists:

```bash
git show-ref --head | sha256sum
git fsck --full --strict
git rev-list --all --objects | grep -E \
  '(^| )(.env.bak|app-live.log|boot.log|run-error.log|new.txt|backups/switching_db_legacy_)' \
  && exit 1 || true
```

Review the redacted Gitleaks JSON. A path-only purge cannot remove a secret that
was copied into another file, commit message, tag message, or ref.

## Step 7 — Replace the remote

After security and release approval:

```bash
cd switching-rewrite.git
git push --mirror --force <repository-url>
```

Reapply branch protection immediately if the hosting platform reset it. Run:

```bash
GITHUB_TOKEN=<admin-token> \
  ./scripts/configure_branch_protection.sh owner/repository main
```

Use an operator-scoped token through the approved secret-delivery mechanism;
do not place it in a shell script or committed env file.

## Step 8 — Invalidate old clones

All existing clones contain the old objects. Instruct users and CI runners to
delete and reclone. Do not recommend `git pull`, merge, or rebase from an old
clone. Remove stale forks, cached artifacts, bundles, release archives, and CI
workspaces where policy allows.

## Step 9 — Post-rewrite monitoring

- run the Security Gates workflow on the rewritten `main` branch;
- verify all required checks are enforced;
- search package registries and release assets for contaminated source archives;
- monitor for use of revoked credentials;
- retain incident evidence according to the approved retention policy.

## Failure handling

If validation fails, do not push. Delete only the disposable rewrite mirror,
copy again from the protected pre-rewrite mirror, correct the path policy, and
repeat. If a force push has already occurred, keep the repository frozen and
coordinate restoration or a second rewrite through the incident commander.
