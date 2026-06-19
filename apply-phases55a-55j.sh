#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ -f pom.xml ]] || fail "run this script from the Switching project root"
[[ -f config/phase55-golive-plan.yaml ]] || fail "Phase 55 files are not fully overlaid"
[[ -f scripts/verify_phase55_static.py ]] || fail "missing Phase 55 static verifier"
[[ -f PHASES_55A_55J_FILE_MANIFEST.txt ]] || fail "missing Phase 55 file manifest"

# ZIP extraction does not preserve executable bits consistently on every platform.
find scripts/golive -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod +x {} +
chmod +x apply-phases55a-55j.sh scripts/verify_phase55_static.py

# The apply operation performs repository checks only. It never invokes a live phase.
PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phase55_static.py

if [[ "${PHASE55_RUN_FRAMEWORK_TESTS:-false}" == "true" ]]; then
  PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v scripts.golive.tests.test_phase55_framework
fi

if [[ "${PHASE55_RUN_PRIOR_STATIC_GATES:-false}" == "true" ]]; then
  PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phase53b_schema_alignment.py
  PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phases_53c_53j.py
  PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phases_54a_54j.py
fi

if [[ "${PHASE55_RUN_REPOSITORY_HYGIENE:-false}" == "true" ]]; then
  security/scripts/verify-repository-hygiene.sh
fi

cat <<'MSG'
Phase 55A-55J changed files applied successfully.
No Production action has been executed.
Follow docs/runbooks/PHASE55_PRODUCTION_GOLIVE.md and execute phases in order on protected runners.
Production remains NO-GO until all Phase 55 evidence is PASS for one immutable release identity.
MSG
