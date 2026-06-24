#!/usr/bin/env python3
from __future__ import annotations
import argparse,datetime as dt,hashlib,json,pathlib,re
PHASES=[f'74{x}' for x in 'ABCDEFGHI']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--phase74-root',required=True); ap.add_argument('--attestation',required=True); ap.add_argument('--output',required=True); ap.add_argument('--commit',required=True); ap.add_argument('--application-digest',required=True); ap.add_argument('--migration-digest',required=True); a=ap.parse_args()
 root=pathlib.Path(a.phase74_root).resolve(); errors=[]; results=[]
 for ph in PHASES:
  p=root/ph/'result.json'
  if not p.is_file(): errors.append(f'missing {ph} result'); continue
  d=json.loads(p.read_text()); results.append({'phase':ph,'status':d.get('status'),'path':str(p),'sha256':sha(p)})
  if d.get('status')!='PASS': errors.append(f'{ph} not PASS')
  if d.get('commit')!=a.commit: errors.append(f'{ph} commit mismatch')
  if d.get('applicationImageDigest')!=a.application_digest or d.get('migrationImageDigest')!=a.migration_digest: errors.append(f'{ph} image digest mismatch')
 att=pathlib.Path(a.attestation).resolve()
 if not att.is_file(): errors.append('entry attestation missing')
 else:
  ad=json.loads(att.read_text())
  if ad.get('kind')!='phase54-entry' or ad.get('status')!='PASS' or ad.get('decision')!='GO': errors.append('entry attestation is not a PASS/GO decision')
  if ad.get('commit')!=a.commit or ad.get('applicationImageDigest')!=a.application_digest or ad.get('migrationImageDigest')!=a.migration_digest: errors.append('entry attestation identity mismatch')
 artifacts=[]
 for p in sorted(root.rglob('*')):
  if p.is_file() and not p.is_symlink(): artifacts.append({'path':str(p.relative_to(root)),'size':p.stat().st_size,'sha256':sha(p)})
 doc={'schemaVersion':1,'generatedAt':dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00','Z'),'status':'PASS' if not errors else 'FAIL','commit':a.commit,'applicationImageDigest':a.application_digest,'migrationImageDigest':a.migration_digest,'phaseResults':results,'attestation':{'path':str(att),'sha256':sha(att)} if att.is_file() else None,'artifacts':artifacts,'errors':errors}
 out=pathlib.Path(a.output); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n'); out.with_suffix(out.suffix+'.sha256').write_text(f'{sha(out)}  {out.name}\n')
 print(json.dumps({'status':doc['status'],'phases':len(results),'artifacts':len(artifacts),'errors':errors})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
