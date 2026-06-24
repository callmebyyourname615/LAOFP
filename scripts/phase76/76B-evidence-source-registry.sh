#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
python3 - <<'PY'
import yaml
from pathlib import Path
p=Path('config/phase76/evidence-source-registry.yaml')
d=yaml.safe_load(p.read_text())
assert len(d['sources'])>=10
assert all('control_id' in x for x in d['sources'])
PY
write_result 76B PASS "Evidence source registry validated"
