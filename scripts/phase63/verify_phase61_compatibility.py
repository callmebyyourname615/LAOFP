#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, subprocess
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_LATEST=101
EXPECTED_COUNT=96
EXPECTED_RESERVED=[88,89,90,98,99]
REQUIRED_PHASES=[f'61{x}' for x in 'ABCDEFGHIJ']


def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,default=Path('.')); p.add_argument('--output',type=Path,required=True); a=p.parse_args(); root=a.root.resolve(); errors=[]; warnings=[]
 versions=[]
 for path in (root/'src/main/resources/db/migration').glob('V*__*.sql'):
  m=re.match(r'V(\d+)__',path.name)
  if m: versions.append(int(m.group(1)))
 missing=sorted(set(range(1,EXPECTED_LATEST+1))-set(versions))
 if len(versions)!=EXPECTED_COUNT: errors.append(f'expected {EXPECTED_COUNT} migrations, found {len(versions)}')
 if max(versions,default=0)!=EXPECTED_LATEST: errors.append(f'expected latest V{EXPECTED_LATEST}')
 if missing!=EXPECTED_RESERVED: errors.append(f'expected reserved gaps {EXPECTED_RESERVED}, found {missing}')
 for phase in REQUIRED_PHASES:
  if not list((root/'scripts/phase61').glob(f'{phase}-*.sh')): errors.append(f'missing Phase 61 script for {phase}')
 legacy=root/'scripts/verify_phase61_static.py'
 phase61b=root/'scripts/phase61/61B-migration-data-integrity.sh'
 legacy_text=legacy.read_text(encoding='utf-8') if legacy.is_file() else ''
 phase61b_text=phase61b.read_text(encoding='utf-8') if phase61b.is_file() else ''
 if 'len(versions)!=90' in legacy_text or '--count 90' in phase61b_text:
  warnings.append('legacy Phase 61 inventory expectations are stale for the merged 96-migration baseline; Phase 63 uses the current inventory without modifying Phase 61 files')
 helpers=['scripts/phase61/verify_smos_hardening.py','scripts/phase61/verify_dashboard_promotion_readiness.py','scripts/phase61/verify_phase61_evidence_tools.py']
 helper_results={}
 for rel in helpers:
  proc=subprocess.run(['python3',rel],cwd=root,text=True,capture_output=True,timeout=120)
  helper_results[rel]={'exitCode':proc.returncode,'stdout':proc.stdout.strip(),'stderr':proc.stderr.strip()}
  if proc.returncode: errors.append(f'{rel} failed')
 doc={'schemaVersion':1,'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'passed':not errors,'migrationCount':len(versions),'latestMigration':max(versions,default=0),'reservedGaps':missing,'legacyVerifierWarnings':warnings,'helperResults':helper_results,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f"Phase 63B Phase 61 compatibility: {'PASS' if not errors else 'FAIL'}")
 for w in warnings: print('  WARNING:',w)
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
