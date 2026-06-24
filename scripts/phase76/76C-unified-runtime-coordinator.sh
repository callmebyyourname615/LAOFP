#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
python3 - <<'PY'
import yaml
from pathlib import Path
d=yaml.safe_load(Path('config/phase76/coordinator-plan.yaml').read_text())
ids={x['id'] for x in d['steps']}
assert all(set(x.get('depends_on',[]))<=ids for x in d['steps'])
PY
write_result 76C PASS "Coordinator dependency graph validated"
