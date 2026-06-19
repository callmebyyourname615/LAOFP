#!/usr/bin/env bash
# Action #4 — Read-only checklist for Phase 53A credential rotation.
# This script CANNOT rotate credentials for you. It only audits what's pending.
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "Credential rotation status (Phase 53A):"
echo

WARN=0
check_placeholder() {
  local file="$1" pattern="$2" label="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    echo "  ⚠️  $label still has placeholder default in $file"
    WARN=$((WARN+1))
  else
    echo "  ✅ $label OK in $file"
  fi
}

# Production config must NOT have placeholders
if [ -f .env.prod.example ]; then
  if grep -qE "change_me|placeholder|your-" .env.prod.example; then
    echo "  ⚠️  .env.prod.example still contains 'change_me' / 'your-' / 'placeholder'"
    WARN=$((WARN+1))
  else
    echo "  ✅ .env.prod.example has no placeholder markers"
  fi
fi

# Dev config: placeholders are OK but flagged for awareness
echo
echo "Dev compose (placeholders are accepted — flagged for awareness):"
check_placeholder docker-compose.yml "switching_app_password_change_me" "DB_APP_PASSWORD"
check_placeholder docker-compose.yml "switching_flyway_password_change_me" "FLYWAY_PASSWORD"
check_placeholder docker-compose.yml "switching_replicator_password_change_me" "REPLICATION_PASSWORD"

echo
echo "Reminders (operator must do manually):"
echo "  [ ] Rotate postgres / replication / app / flyway / archive / minio creds in prod Vault"
echo "  [ ] Run security/scripts/purge-sensitive-history.sh + force-push (coordinated)"
echo "  [ ] Invalidate all old clones + CI caches"
echo "  [ ] Sign SECRET_ROTATION_CHECKLIST.md and store in evidence/"
echo
echo "See: docs/security/SECRET_ROTATION_CHECKLIST.md"
echo "     docs/security/GIT_HISTORY_PURGE_RUNBOOK.md"
echo
echo "Audit complete (warnings: $WARN). This step always exits 0 — operator decides."
