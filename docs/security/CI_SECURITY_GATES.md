# CI Security Gates

The CI package job is blocked by the reusable `Security Gates` workflow.

| Gate | Failure policy |
|---|---|
| Gitleaks | Detected committed secret fails the job |
| Dependency Review | High-severity vulnerable dependency added by a PR fails |
| OWASP Dependency Check | CVSS 7.0 or above fails |
| Trivy filesystem | HIGH/CRITICAL fixed findings fail |
| Trivy container | HIGH/CRITICAL fixed findings fail |
| CodeQL | Findings are uploaded to GitHub code scanning |

## Required repository settings

- Store `NVD_API_KEY` in GitHub Actions secrets.
- Self-hosted Actions runners must be version `2.327.1` or newer for the Node.js 24 actions used here.
- Give the workflow `security-events: write` permission.
- Require `Full Maven Test Suite` and all security jobs on `main`.
- Prevent force pushes and require pull-request review.

Use:

```bash
GITHUB_TOKEN=<admin-token> ./scripts/configure_branch_protection.sh owner/repository main
```

Review scanner exceptions in code review. Do not suppress a finding globally
without an owner, expiry date, and documented risk acceptance.
