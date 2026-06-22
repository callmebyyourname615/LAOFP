#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
usage(){ echo "usage: $0 <56A|56B|56C|56D|56E|56F|56G|56H|56I|56J|status>"; }
case "${1:-}" in
 56A) exec scripts/day2/56a-slo-error-budget.sh;; 56B) exec scripts/day2/56b-continuous-reconciliation.sh;;
 56C) exec scripts/day2/56c-ha-failover-certification.sh;; 56D) exec scripts/day2/56d-capacity-autoscaling.sh;;
 56E) exec scripts/day2/56e-security-operations.sh;; 56F) exec scripts/day2/56f-compliance-evidence.sh;;
 56G) exec scripts/day2/56g-progressive-delivery.sh;; 56H) exec scripts/day2/56h-incident-management.sh;;
 56I) exec scripts/day2/56i-finops-efficiency.sh;; 56J) exec scripts/day2/56j-resilience-certification.sh;;
 status) python3 - <<'PY'
import json,pathlib
root=pathlib.Path('build/phase56-day2/phases')
for p in 'ABCDEFGHIJ':
 f=root/f'56{p}'/'result.json'; print(f'56{p}: '+(json.loads(f.read_text())['status'] if f.exists() else 'NOT_RUN'))
PY
 ;;
 *) usage; exit 2;; esac
