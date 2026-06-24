#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source scripts/phase77/common.sh
python3 - <<'PY'
import yaml
from pathlib import Path
d=yaml.safe_load(Path('config/phase77/reconciliation-policy.yaml').read_text())
assert len(d['checks']) >= 8
assert Path('sql/phase77/continuous_financial_reconciliation.sql').is_file()
PY
write_result 77C PASS "Continuous financial reconciliation policy validated; runtime execution not asserted"
