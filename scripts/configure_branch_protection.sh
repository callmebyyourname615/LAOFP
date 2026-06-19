#!/usr/bin/env bash
set -Eeuo pipefail

REPOSITORY="${1:-}"
BRANCH="${2:-main}"

if [[ -z "${REPOSITORY}" ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "Usage: $0 <owner/repository> [branch]" >&2
    exit 64
  fi
  REPOSITORY="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
fi

command -v gh >/dev/null 2>&1 || { echo "GitHub CLI (gh) is required" >&2; exit 69; }
gh auth status >/dev/null

# Check names must match the job names in .github/workflows/ci.yml and security.yml.
gh api --method PUT "repos/${REPOSITORY}/branches/${BRANCH}/protection" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "Full Maven Test Suite",
      "Package Executable JAR"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_linear_history": true,
  "allow_fork_syncing": true
}
JSON

echo "Branch protection configured for ${REPOSITORY}:${BRANCH}"
