#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
usage(){ echo "usage: $0 <57A|57B|57C|57D|57E|57F|57G|57H|57I|57J|status>"; }
case "${1:-}" in
 57A) exec scripts/enterprise/57a-continuous-recertification.sh;;
 57B) exec scripts/enterprise/57b-multi-region-dr.sh;;
 57C) exec scripts/enterprise/57c-ledger-financial-control.sh;;
 57D) exec scripts/enterprise/57d-data-lifecycle-governance.sh;;
 57E) exec scripts/enterprise/57e-fraud-aml-operations.sh;;
 57F) exec scripts/enterprise/57f-observability-intelligence.sh;;
 57G) exec scripts/enterprise/57g-self-service-operations.sh;;
 57H) exec scripts/enterprise/57h-vulnerability-lifecycle.sh;;
 57I) exec scripts/enterprise/57i-dependency-resilience.sh;;
 57J) exec scripts/enterprise/57j-enterprise-maturity-certification.sh;;
 status) python3 - <<'PY'
import json,pathlib,os
root=pathlib.Path(os.environ.get('ENTERPRISE_ROOT','build/phase57-enterprise'))/'phases'
for p in 'ABCDEFGHIJ':
 f=root/f'57{p}'/'result.json'; print(f'57{p}: '+(json.loads(f.read_text())['status'] if f.exists() else 'NOT_RUN'))
PY
 ;;
 *) usage; exit 2;;
esac
