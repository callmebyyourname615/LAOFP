#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
python3 - <<'PY'
import yaml
from pathlib import Path
d=yaml.safe_load(Path('config/phase76/approval-policy.yaml').read_text())
assert len(d['required_roles'])>=5
assert d['separation_of_duties']['single_person_all_roles'] is False
assert d['separation_of_duties']['minimum_unique_approvers'] >= 4
PY
write_result 76F PASS "Approval policy and separation-of-duties validated"
