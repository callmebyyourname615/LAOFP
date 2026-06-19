#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this script inside the Switching Git repository after overlaying the Phase 53A ZIP." >&2
  exit 2
}
cd "$ROOT"
manifest="PHASE_53A_DELETE_MANIFEST.txt"
[[ -f "$manifest" ]] || { echo "Missing $manifest" >&2; exit 2; }

removed=0
while IFS= read -r path; do
  [[ -n "$path" && "$path" != \#* ]] || continue
  case "$path" in
    /*|*".."*) echo "Unsafe manifest path rejected: $path" >&2; exit 2 ;;
  esac
  if git ls-files --error-unmatch -- "$path" >/dev/null 2>&1; then
    git rm -f -- "$path" >/dev/null
    echo "Removed tracked prohibited artifact: $path"
    removed=$((removed + 1))
  elif [[ -e "$path" ]]; then
    rm -f -- "$path"
    echo "Removed untracked prohibited artifact: $path"
    removed=$((removed + 1))
  fi
done < "$manifest"
rmdir backups 2>/dev/null || true

chmod +x security/scripts/*.sh security/scripts/*.py security/tests/*.sh .githooks/pre-commit 2>/dev/null || true
security/scripts/verify-repository-hygiene.sh
if [[ -f .env ]]; then
  if ! security/scripts/check-local-env.sh .env; then
    echo "WARNING: existing local .env is not Phase 53A compliant." >&2
    echo "Review it, rotate any reused values, then run: security/scripts/generate-local-env.sh --force" >&2
  fi
fi

echo "Phase 53A working-tree cleanup complete; removed $removed artifact(s)."
echo "Mandatory next step: rotate exposed credentials, then purge Git history using the documented mirror-clone runbook."
