#!/usr/bin/env python3
from __future__ import annotations
import json,subprocess
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
REQUIRED=[
 'AGENT/PHASE66_IMPLEMENTATION_CHECKLIST.md','scripts/phase66/common.sh','scripts/phase66/run_phase66.sh',
 *[f'scripts/phase66/66{x}-{name}.sh' for x,name in zip('ABCDEFGHIJ',[
 'phase65-merge-collision-guard','uat-connectivity-preflight','build-test-runtime-closure','migration-data-certification','performance-campaign','backup-pitr-restore','dr-failure-campaign','secret-rotation-ceremony','smos-live-security','build-runtime-closure-bundle'])],
 'config/phase66/file-boundary.yaml','config/phase66/uat-dependencies.yaml','config/phase66/performance-thresholds.yaml','config/phase66/dr-scenarios.yaml','config/phase66/smos-runtime-checks.yaml',
 'schemas/phase66/runtime-closure-manifest.schema.json','scripts/phase66/run_database_failover_drill.sh','scripts/phase66/record_dr_scenario_results.py','sql/phase66/data-integrity-checks.sql','docs/phase66/PHASE66_OPERATOR_GUIDE.md','.github/workflows/phase66-runtime-closure.yml','scripts/execute-and-verify/09-phase66-runtime-closure.sh']

def main():
 missing=[x for x in REQUIRED if not (ROOT/x).is_file()]
 if missing: raise SystemExit('missing Phase 66 files: '+', '.join(missing))
 for p in sorted((ROOT/'scripts/phase66').glob('*.sh'))+[ROOT/'scripts/execute-and-verify/09-phase66-runtime-closure.sh']:
  subprocess.run(['bash','-n',str(p)],check=True)
 for p in sorted((ROOT/'schemas/phase66').glob('*.json')): json.loads(p.read_text())
 for p in sorted((ROOT/'config/phase66').glob('*.yaml')): yaml.safe_load(p.read_text())
 yaml.safe_load((ROOT/'.github/workflows/phase66-runtime-closure.yml').read_text())
 protected=['src/main/','src/test/','src/main/resources/db/migration/','scripts/phase65/','docs/phase65/','pom.xml']
 contract=yaml.safe_load((ROOT/'config/phase66/file-boundary.yaml').read_text())
 if not all(any(x in pat for pat in contract['protectedPaths']) for x in protected): raise SystemExit('protected path contract incomplete')
 text=(ROOT/'scripts/phase66/build_runtime_closure_bundle.py').read_text()
 if "CERTIFIED' if all_pass and approval_ok" not in text: raise SystemExit('no-false-certification guard missing')
 print(f'Phase 66 static contract PASS: {len(REQUIRED)} required files, shell/JSON/YAML/boundary checks green')
 return 0
if __name__=='__main__':raise SystemExit(main())
