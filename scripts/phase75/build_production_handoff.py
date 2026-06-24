#!/usr/bin/env python3
from __future__ import annotations
import argparse,datetime as dt,hashlib,json,pathlib
PHASES=[f'75{x}' for x in 'ABCDEFGHI']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--phase75-root',required=True); ap.add_argument('--phase54-manifest',required=True); ap.add_argument('--decision-attestation',required=True); ap.add_argument('--output',required=True); ap.add_argument('--commit',required=True); ap.add_argument('--application-digest',required=True); ap.add_argument('--migration-digest',required=True); a=ap.parse_args(); root=pathlib.Path(a.phase75_root).resolve(); errors=[]; phases=[]
 for ph in PHASES:
  p=root/ph/'result.json'
  if not p.is_file(): errors.append('missing '+ph); continue
  d=json.loads(p.read_text()); phases.append({'phase':ph,'status':d.get('status'),'sha256':sha(p)})
  if d.get('status')!='PASS': errors.append(ph+' not PASS')
  if d.get('commit')!=a.commit: errors.append(ph+' commit mismatch')
  if d.get('applicationImageDigest')!=a.application_digest or d.get('migrationImageDigest')!=a.migration_digest: errors.append(ph+' digest mismatch')
 manifest=pathlib.Path(a.phase54_manifest).resolve(); decision=pathlib.Path(a.decision_attestation).resolve()
 if not manifest.is_file(): errors.append('Phase 54 manifest missing')
 else:
  m=json.loads(manifest.read_text()); rel=m.get('release',{})
  if not m.get('releaseCandidateReady'): errors.append('Phase 54 release candidate not ready')
  if rel.get('gitCommit')!=a.commit or rel.get('imageDigest')!=a.application_digest: errors.append('Phase 54 identity mismatch')
 if not decision.is_file(): errors.append('production decision missing')
 else:
  dd=json.loads(decision.read_text())
  if dd.get('kind')!='production-decision' or dd.get('status')!='PASS' or dd.get('decision')!='GO': errors.append('production decision is not PASS/GO')
  if dd.get('commit')!=a.commit or dd.get('applicationImageDigest')!=a.application_digest or dd.get('migrationImageDigest')!=a.migration_digest: errors.append('production decision identity mismatch')
 artifacts=[]
 for p in sorted(root.rglob('*')):
  if p.is_file() and not p.is_symlink(): artifacts.append({'path':str(p.relative_to(root)),'sha256':sha(p),'size':p.stat().st_size})
 doc={'schemaVersion':1,'generatedAt':dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00','Z'),'decision':'GO' if not errors else 'NO-GO','commit':a.commit,'applicationImageDigest':a.application_digest,'migrationImageDigest':a.migration_digest,'phase75Results':phases,'phase54Manifest':{'path':str(manifest),'sha256':sha(manifest)} if manifest.is_file() else None,'decisionAttestation':{'path':str(decision),'sha256':sha(decision)} if decision.is_file() else None,'artifacts':artifacts,'errors':errors}
 out=pathlib.Path(a.output); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n'); out.with_suffix(out.suffix+'.sha256').write_text(f'{sha(out)}  {out.name}\n'); print(json.dumps({'decision':doc['decision'],'errors':errors})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
