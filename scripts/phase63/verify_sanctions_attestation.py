#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
PLACEHOLDER=re.compile(r'(?i)(replace|todo|tbd|change_me|example)')

def text(v): return isinstance(v,str) and bool(v.strip()) and not PLACEHOLDER.search(v)

def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--attestation',type=Path,required=True); p.add_argument('--runtime-log',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
 data=json.loads(a.attestation.read_text(encoding='utf-8')); errors=[]
 if not a.runtime_log.is_file() or a.runtime_log.stat().st_size==0: errors.append('sanctions runtime log is missing or empty')
 if data.get('schemaVersion')!=1: errors.append('schemaVersion must equal 1')
 for key in ('syncCompleted','providerUidUnique','laoNormalizationVerified','duplicateHandlingVerified','staleDataAlertVerified','screeningRegressionPassed'):
  if data.get(key) is not True: errors.append(f'{key} must be true')
 try:
  if int(data.get('providerRecordCount',0))<=0: errors.append('providerRecordCount must be positive')
  if int(data.get('duplicateProviderUidCount',-1))!=0: errors.append('duplicateProviderUidCount must be zero')
 except Exception: errors.append('provider counts must be integers')
 for key in ('provider','datasetVersion','complianceLead','qaLead','signedAt','changeReference'):
  if not text(data.get(key)): errors.append(f'{key} missing or placeholder')
 report={'schemaVersion':1,'passed':not errors,'errors':errors,'attestation':data}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f"Phase 63I sanctions verification: {'PASS' if not errors else 'FAIL'}")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
