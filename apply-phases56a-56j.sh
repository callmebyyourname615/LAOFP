#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
[[ -f pom.xml ]] || { echo 'run from Switching project root' >&2; exit 2; }
[[ -f config/phase56-day2-plan.yaml && -f scripts/verify_phase56_static.py ]] || { echo 'Phase 56 overlay incomplete' >&2; exit 2; }
find scripts/day2 -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod +x {} +
chmod +x apply-phases56a-56j.sh scripts/verify_phase56_static.py
PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phase56_static.py
if [[ "${PHASE56_RUN_FRAMEWORK_TESTS:-false}" == true ]]; then PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v scripts.day2.tests.test_phase56_framework; fi
if [[ "${PHASE56_RUN_PRIOR_STATIC_GATES:-false}" == true ]]; then
 python3 scripts/verify_phase53b_schema_alignment.py
 python3 scripts/verify_phases_53c_53j.py
 python3 scripts/verify_phases_54a_54j.py
 python3 scripts/verify_phase55_static.py
fi
cat <<'MSG'
Phase 56A-56J changed files applied successfully.
No production, failover, deployment, containment, or DR action was executed.
Use docs/runbooks/PHASE56_DAY2_OPERATIONS.md on protected runners.
MSG
