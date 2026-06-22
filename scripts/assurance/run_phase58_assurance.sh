#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
case "${1:-}" in
 58A) exec scripts/assurance/58a-regulatory-reporting.sh;; 58B) exec scripts/assurance/58b-participant-governance.sh;; 58C) exec scripts/assurance/58c-crypto-agility.sh;; 58D) exec scripts/assurance/58d-privacy-engineering.sh;; 58E) exec scripts/assurance/58e-decision-governance.sh;; 58F) exec scripts/assurance/58f-iso20022-lifecycle.sh;; 58G) exec scripts/assurance/58g-settlement-risk.sh;; 58H) exec scripts/assurance/58h-digital-twin.sh;; 58I) exec scripts/assurance/58i-third-party-risk.sh;; 58J) exec scripts/assurance/58j-supervisory-readiness.sh;;
 status) python3 - <<'PY2'
import json,pathlib,os
root=pathlib.Path(os.environ.get('ASSURANCE_ROOT','build/phase58-assurance'))/'phases'
for x in 'ABCDEFGHIJ':
 f=root/f'58{x}'/'result.json'; print(f'58{x}: '+(json.loads(f.read_text())['status'] if f.exists() else 'NOT_RUN'))
PY2
 ;;
 *) echo "usage: $0 <58A|58B|58C|58D|58E|58F|58G|58H|58I|58J|status>"; exit 2;; esac
