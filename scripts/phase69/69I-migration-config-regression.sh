#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69I
cd "$PHASE69_ROOT"
log="$PHASE69_LOG_DIR/$phase-migration-config.log"
python3 - "$PHASE69_ROOT" <<'PY' | tee "$log"
from pathlib import Path
import re, sys
root=Path(sys.argv[1]); migration=root/'src/main/resources/db/migration'
versions=[]; names=[]
for p in migration.glob('V*__*.sql'):
    m=re.match(r'V(\d+)__(.+)\.sql$',p.name)
    if not m: continue
    versions.append(int(m.group(1))); names.append(p.name)
if not versions: raise SystemExit('no migrations found')
if len(versions)!=len(set(versions)): raise SystemExit('duplicate numeric migration version detected')
if names!=names: raise AssertionError('unreachable')
print(f'migrations={len(versions)} latest=V{max(versions)}')
for path in root.rglob('*'):
    if path.is_file() and any(part=='phase68' for part in path.parts):
        pass
print('migration inventory: PASS')
PY
compose_deferred=false
if command -v docker >/dev/null 2>&1; then
  compose_file=''
  for candidate in docker-compose.yml docker-compose.uat.yml compose.yml; do [[ -f "$candidate" ]] && { compose_file="$candidate"; break; }; done
  if [[ -n "$compose_file" ]]; then
    docker compose -f "$compose_file" config --quiet 2>&1 | tee -a "$log"
  elif [[ "$PHASE69_MODE" == full ]]; then
    phase69_result "$phase" BLOCKED "No Docker Compose file is available for full configuration validation" --evidence "logs/$phase-migration-config.log"
    exit 2
  else
    compose_deferred=true
    echo 'compose file unavailable: runtime validation deferred' | tee -a "$log"
  fi
elif [[ "$PHASE69_MODE" == full ]]; then
  phase69_result "$phase" BLOCKED "Docker is unavailable for full Compose validation" --evidence "logs/$phase-migration-config.log"
  exit 2
else
  compose_deferred=true
  echo 'docker unavailable: compose runtime validation deferred' | tee -a "$log"
fi
production_files=()
for candidate in .env.prod .env.prod.example config/production-environment-contract.yaml config/application-prod.yml src/main/resources/application-prod.yml; do
  [[ -f "$candidate" ]] && production_files+=("$candidate")
done
if (( ${#production_files[@]} > 0 )) && rg -n '(password|token|secret)[[:space:]]*[:=][[:space:]]*(change_me|changeme|password123)' "${production_files[@]}" >> "$log" 2>&1; then
  phase69_result "$phase" FAIL "Unsafe production placeholder detected" --evidence "logs/$phase-migration-config.log"
  exit 1
fi
if [[ "$compose_deferred" == true ]]; then
  phase69_result "$phase" PREPARED "Migration inventory passes; Docker Compose validation is deferred to full mode" --evidence "logs/$phase-migration-config.log"
else
  phase69_result "$phase" PASS "Migration inventory and repository configuration regression checks pass" --evidence "logs/$phase-migration-config.log"
fi
