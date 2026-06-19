#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: security/scripts/scan-git-history.sh [options]

Options:
  --output-dir DIR   Evidence directory (default: build/security-history)
  --skip-gitleaks    Run forbidden-path history checks only
  --help             Show this message

The command never prints matching secret values. Gitleaks output is redacted and
written to an ignored evidence directory with mode 0600 where supported.
USAGE
}

OUTPUT_DIR="build/security-history"
SKIP_GITLEAKS=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      [[ $# -ge 2 ]] || { echo "--output-dir requires a value" >&2; exit 64; }
      OUTPUT_DIR="$2"; shift 2 ;;
    --skip-gitleaks) SKIP_GITLEAKS=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 64 ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this command inside the Switching Git repository." >&2
  exit 2
}
cd "$ROOT"
mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR" 2>/dev/null || true

PURGE_LIST="security/policy/history-purge-paths.txt"
[[ -f "$PURGE_LIST" ]] || { echo "Missing $PURGE_LIST" >&2; exit 2; }

history_file="$(mktemp)"
trap 'rm -f "$history_file"' EXIT
git log --all --format= --name-only | sed '/^$/d' | sort -u > "$history_file"
violations=()
while IFS= read -r forbidden; do
  [[ -n "$forbidden" && "$forbidden" != \#* ]] || continue
  if grep -Fxq -- "$forbidden" "$history_file"; then
    violations+=("$forbidden")
  fi
done < "$PURGE_LIST"

printf '{\n  "schema_version": 1,\n  "forbidden_paths_found": [' > "$OUTPUT_DIR/path-scan.json"
for i in "${!violations[@]}"; do
  [[ "$i" -eq 0 ]] || printf ', ' >> "$OUTPUT_DIR/path-scan.json"
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]), end="")' "${violations[$i]}" >> "$OUTPUT_DIR/path-scan.json"
done
printf ']\n}\n' >> "$OUTPUT_DIR/path-scan.json"
chmod 600 "$OUTPUT_DIR/path-scan.json" 2>/dev/null || true

path_status=0
if (( ${#violations[@]} > 0 )); then
  echo "Git history path scan FAILED: ${#violations[@]} prohibited path(s) remain:" >&2
  printf '  - %s\n' "${violations[@]}" >&2
  path_status=1
else
  echo "Git history path scan passed."
fi

if [[ "$SKIP_GITLEAKS" == true ]]; then
  exit "$path_status"
fi

GITLEAKS_BIN="${GITLEAKS_BIN:-gitleaks}"
if ! command -v "$GITLEAKS_BIN" >/dev/null 2>&1; then
  echo "gitleaks is required. Install it or set GITLEAKS_BIN. No fallback silently skips the scan." >&2
  exit 69
fi

report="$OUTPUT_DIR/gitleaks-history.json"
set +e
if "$GITLEAKS_BIN" git --help >/dev/null 2>&1; then
  "$GITLEAKS_BIN" git --redact --report-format json --report-path "$report" .
  gitleaks_status=$?
else
  # Compatibility with Gitleaks releases that use the detect subcommand.
  "$GITLEAKS_BIN" detect --source . --redact --report-format json --report-path "$report"
  gitleaks_status=$?
fi
set -e
[[ -f "$report" ]] && chmod 600 "$report" 2>/dev/null || true

if [[ "$gitleaks_status" -ne 0 ]]; then
  echo "Gitleaks history scan failed or found a secret. Review the redacted report: $report" >&2
fi

if [[ "$path_status" -ne 0 || "$gitleaks_status" -ne 0 ]]; then
  exit 1
fi

echo "Full Git history secret scan passed."
