#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
python3 - <<'PY'
import yaml
from pathlib import Path
d=yaml.safe_load(Path('config/phase76/waiver-policy.yaml').read_text())
assert {'TRANSACTION_LOSS','LEDGER_IMBALANCE','SECRET_LEAK'} <= set(d['non_waivable'])
PY
write_result 76G PASS "Non-waivable risk controls validated"
