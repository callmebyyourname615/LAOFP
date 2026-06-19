#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage:
  security/scripts/purge-sensitive-history.sh [--repo PATH] [--output-dir DIR]
  security/scripts/purge-sensitive-history.sh --execute --acknowledge-credential-rotation [--repo PATH] [--output-dir DIR]

Default behavior is a dry run. Execution is deliberately restricted to a bare or
mirror clone and never performs a force push. Follow the runbook after review.
USAGE
}

REPO="."
OUTPUT_DIR="../switching-history-purge-evidence"
EXECUTE=false
ACK_ROTATION=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) [[ $# -ge 2 ]] || exit 64; REPO="$2"; shift 2 ;;
    --output-dir) [[ $# -ge 2 ]] || exit 64; OUTPUT_DIR="$2"; shift 2 ;;
    --execute) EXECUTE=true; shift ;;
    --acknowledge-credential-rotation) ACK_ROTATION=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 64 ;;
  esac
done

REPO="$(cd "$REPO" && pwd)"
POLICY_SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../policy" && pwd)/history-purge-paths.txt"
[[ -f "$POLICY_SOURCE" ]] || { echo "Missing purge path policy." >&2; exit 2; }

git -C "$REPO" rev-parse --git-dir >/dev/null 2>&1 || { echo "$REPO is not a Git repository" >&2; exit 2; }
is_bare="$(git -C "$REPO" rev-parse --is-bare-repository)"
head_before="$(git -C "$REPO" rev-parse --verify HEAD 2>/dev/null || printf unknown)"
path_count="$(grep -Ev '^[[:space:]]*(#|$)' "$POLICY_SOURCE" | wc -l | tr -d ' ')"

echo "Repository: $REPO"
echo "HEAD before: $head_before"
echo "Paths scheduled for removal: $path_count"
grep -Ev '^[[:space:]]*(#|$)' "$POLICY_SOURCE" | sed 's/^/  - /'
echo

echo "This operation rewrites every reachable commit containing those paths."
echo "It does not rotate credentials and does not force-push remotes."

if [[ "$EXECUTE" != true ]]; then
  echo "DRY RUN ONLY. Re-run with --execute --acknowledge-credential-rotation in a mirror clone."
  exit 0
fi

[[ "$ACK_ROTATION" == true ]] || {
  echo "Execution requires --acknowledge-credential-rotation." >&2
  exit 64
}
[[ "$is_bare" == true ]] || {
  echo "Refusing to rewrite a working clone. Create a mirror clone as documented in GIT_HISTORY_PURGE_RUNBOOK.md." >&2
  exit 1
}
command -v git-filter-repo >/dev/null 2>&1 || {
  echo "git-filter-repo is required and must be installed from the approved package source." >&2
  exit 69
}

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR" 2>/dev/null || true
cp "$POLICY_SOURCE" "$OUTPUT_DIR/history-purge-paths.txt"

{
  echo "started_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "repository=$(basename "$REPO")"
  echo "head_before=$head_before"
  echo "path_count=$path_count"
} > "$OUTPUT_DIR/purge-metadata.env"
chmod 600 "$OUTPUT_DIR/purge-metadata.env" 2>/dev/null || true

# git-filter-repo expects one literal path per line. Comments and blanks are removed.
tmp_paths="$(mktemp)"
trap 'rm -f "$tmp_paths"' EXIT
grep -Ev '^[[:space:]]*(#|$)' "$POLICY_SOURCE" > "$tmp_paths"

git -C "$REPO" filter-repo --force --invert-paths --paths-from-file "$tmp_paths"
git -C "$REPO" reflog expire --expire=now --all
git -C "$REPO" gc --prune=now --aggressive

head_after="$(git -C "$REPO" rev-parse --verify HEAD 2>/dev/null || printf unknown)"
{
  echo "finished_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "head_after=$head_after"
} >> "$OUTPUT_DIR/purge-metadata.env"

echo "History rewrite completed locally. HEAD after: $head_after"
echo "Do not push until the post-purge scans and rotation checklist are complete."
