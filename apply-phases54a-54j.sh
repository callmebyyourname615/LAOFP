#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
validate_relative_path() {
  local path="$1"
  [[ -n "$path" ]] || fail "empty manifest path"
  [[ "$path" != /* ]] || fail "absolute manifest path is prohibited: $path"
  [[ "$path" != *$'\n'* && "$path" != *$'\r'* ]] || fail "invalid manifest path"
  case "/$path/" in */../*|*/./*) fail "unsafe manifest path: $path";; esac
}

[[ -f pom.xml ]] || fail "run this script from the Switching project root"
[[ -f config/phase54-certification-plan.yaml ]] || fail "Phase 54 changed files are not fully overlaid"
[[ -f PHASE_54_DELETE_MANIFEST.txt ]] || fail "missing delete manifest"
[[ -f PHASE_54_UNTRACK_MANIFEST.txt ]] || fail "missing untrack manifest"

# Preserve developer-local secrets while ensuring they are no longer tracked.
while IFS= read -r path || [[ -n "$path" ]]; do
  [[ -z "$path" || "$path" == \#* ]] && continue
  validate_relative_path "$path"
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if git ls-files --error-unmatch -- "$path" >/dev/null 2>&1; then
      git rm --cached --ignore-unmatch -- "$path" >/dev/null
      printf 'Untracked sensitive local file: %s\n' "$path"
    fi
  fi
  [[ ! -f "$path" ]] || chmod 0600 "$path" 2>/dev/null || true
done < PHASE_54_UNTRACK_MANIFEST.txt

# Remove generated content. Existing certification evidence is archived outside
# the repository before build/ is cleaned.
if [[ -d build/phase54-certification ]]; then
  archive="${ROOT%/*}/Switching-phase54-evidence-before-apply-$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$archive"
  cp -a build/phase54-certification "$archive/"
  printf 'Archived existing Phase 54 evidence to: %s\n' "$archive"
fi
while IFS= read -r path || [[ -n "$path" ]]; do
  [[ -z "$path" || "$path" == \#* ]] && continue
  validate_relative_path "$path"
  normalized="${path%/}"
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git rm -r --cached --ignore-unmatch -- "$normalized" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$normalized"
done < PHASE_54_DELETE_MANIFEST.txt

# Restore executable bits that ZIP extraction may not preserve on every client.
find scripts/certification -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod +x {} +
chmod +x \
  apply-phases54a-54j.sh \
  performance/scripts/capture-capacity-evidence.sh \
  performance/scripts/run-k6.sh \
  performance/settlement/run_settlement_benchmark.sh \
  dr/scripts/capture-baseline.sh \
  dr/scripts/verify-recovery.sh \
  scripts/verify_all_static.py \
  scripts/verify_phases_54a_54j.py

python3 scripts/verify_phases_54a_54j.py

if [[ "${PHASE54_RUN_FRAMEWORK_TESTS:-false}" == "true" ]]; then
  python3 scripts/certification/tests/test_phase54_framework.py
fi

if [[ "${PHASE54_RUN_STATIC_GATES:-false}" == "true" ]]; then
  python3 scripts/certification/run_static_gates.py
fi

if [[ "${PHASE54_RUN_MAVEN:-false}" == "true" ]]; then
  ./mvnw --batch-mode --no-transfer-progress clean verify
fi

cat <<'EOF'
Phase 54A-54J changed files applied successfully.
Runtime certification has NOT been executed by this apply script.
Use docs/runbooks/PHASE54_CERTIFICATION_EXECUTION.md on the approved UAT/performance/DR runner.
EOF
