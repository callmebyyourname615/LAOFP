# CI Security Gates

The Security Gates workflow runs on pull requests to `main`, pushes to `main`,
manual dispatch, weekly schedule, and reusable workflow calls.

| Gate | Failure policy |
|---|---|
| Repository Hygiene | Prohibited tracked files, literal secret assignments, or prohibited paths remaining anywhere in Git history fail |
| Security Policy Static Checks | Sensitive logging, webhook redirect/allowlist, ingress mTLS, or unpinned Kubernetes image violations fail |
| Gitleaks | A secret detected in the complete reachable Git history fails |
| Dependency Review | A high-severity vulnerable dependency introduced by a pull request fails |
| OWASP Dependency Check | CVSS 7.0 or above fails |
| Trivy filesystem | HIGH/CRITICAL fixed repository/dependency findings fail |
| Trivy container | HIGH/CRITICAL fixed container findings fail |
| CodeQL | Java/Kotlin security findings are uploaded to GitHub code scanning |

## Required repository settings

- Store `NVD_API_KEY` in GitHub Actions secrets.
- Self-hosted Actions runners must meet the versions required by pinned actions.
- Give the workflow `security-events: write` permission.
- Require every context configured by `scripts/configure_branch_protection.sh`.
- Enforce pull-request review, stale-review dismissal, last-push approval,
  conversation resolution, linear history, and no force pushes during normal operation.
- Allow a temporary force-push window only for the approved Phase 53A history
  rewrite, then immediately restore protection.

Configure protection:

```bash
GITHUB_TOKEN=<admin-token> \
  ./scripts/configure_branch_protection.sh owner/repository main
```

The token must be injected through an approved operator secret mechanism and must
not be written to `.env`, shell scripts, tickets, or repository artifacts.

## Local parity checks

```bash
security/scripts/verify-repository-hygiene.sh
security/tests/repository-hygiene-test.sh
security/tests/generate-local-env-test.sh
security/scripts/scan-git-history.sh
security/scripts/check-sensitive-logging.sh
```

The full history scan requires Gitleaks. Scanner output and uploaded evidence must
remain redacted and must not be committed.

## Exception policy

Do not suppress a finding globally. Every exception requires:

- a named owner;
- a narrow path/rule scope;
- a documented reason;
- proof that the value is synthetic/non-secret;
- an expiry/review date;
- security approval.
