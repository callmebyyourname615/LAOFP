#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
[[ -f pom.xml ]] || { echo 'Run from the Switching project root.' >&2; exit 2; }
[[ -f config/phase56-day2-plan.yaml && -f scripts/verify_phase56_static.py ]] || { echo 'Phase 56 baseline is required before Phase 57.' >&2; exit 2; }
[[ -f config/phase57-enterprise-plan.yaml && -f scripts/verify_phase57_static.py ]] || { echo 'Phase 57 overlay is incomplete.' >&2; exit 2; }
find scripts/enterprise -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod +x {} +
chmod +x apply-phases57a-57j.sh scripts/verify_phase57_static.py
find scripts/enterprise -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify_phase57_static.py
if [[ "${PHASE57_RUN_FRAMEWORK_TESTS:-false}" == true ]]; then
  PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v scripts.enterprise.tests.test_phase57_framework
fi
if [[ "${PHASE57_RUN_PRIOR_STATIC_GATES:-false}" == true ]]; then
  python3 scripts/verify_phase53b_schema_alignment.py
  python3 scripts/verify_phases_53c_53j.py
  python3 scripts/verify_phases_54a_54j.py
  python3 scripts/verify_phase55_static.py
  python3 scripts/verify_phase56_static.py
fi
if [[ "${PHASE57_RUN_REPOSITORY_HYGIENE:-false}" == true ]]; then
  security/scripts/verify-repository-hygiene.sh
fi
cat <<'MSG'
Phase 57A-57J changed files applied successfully.
No region failover, financial freeze, deletion, fraud hold, self-service operation,
production patch, traffic change, or certificate signing was executed by this apply script.
Use docs/runbooks/enterprise/PHASE57_MASTER_RUNBOOK.md on protected runners.
MSG
