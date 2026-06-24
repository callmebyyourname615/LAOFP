#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source scripts/phase77/common.sh
python3 - <<'PY'
import yaml
from pathlib import Path
d=yaml.safe_load(Path('config/phase77/capacity-policy.yaml').read_text())
assert len(d['guardrails']) >= 5
PY
write_result 77D PASS "Capacity and autoscaling governance policy validated; runtime execution not asserted"
