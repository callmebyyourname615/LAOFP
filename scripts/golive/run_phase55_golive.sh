#!/usr/bin/env bash
set -Eeuo pipefail
phase="${1:-status}"
case "$phase" in
  55A|55B|55C|55D|55E|55F|55G|55H|55I|55J)
    key=$(printf '%s' "$phase" | tr '[:upper:]' '[:lower:]')
    script=$(find scripts/golive -maxdepth 1 -type f -name "${key}-*.sh" -print -quit)
    [[ -n "$script" ]] || { echo "runner not found for $phase" >&2; exit 2; }
    exec "$script"
    ;;
  status)
    root="${GOLIVE_ROOT:-build/phase55-golive}"
    python3 - "$root" <<'PY'
import json,pathlib,sys
root=pathlib.Path(sys.argv[1]); rows=[]
for letter in 'ABCDEFGHIJ':
    phase='55'+letter; p=root/'phases'/phase/'result.json'
    rows.append({'phase':phase,'status':json.loads(p.read_text()).get('status') if p.is_file() else 'NOT_RUN'})
print(json.dumps({'phases':rows},indent=2))
PY
    ;;
  *) echo "usage: $0 {55A|55B|55C|55D|55E|55F|55G|55H|55I|55J|status}" >&2; exit 64;;
esac
