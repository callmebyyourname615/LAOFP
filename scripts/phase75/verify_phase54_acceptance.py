#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,pathlib,re

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--root',default='build/phase54-certification'); ap.add_argument('--phases',nargs='+',required=True); ap.add_argument('--output',required=True); ap.add_argument('--commit',required=True); ap.add_argument('--image-digest',required=True); ap.add_argument('--reference',required=True); a=ap.parse_args()
 root=pathlib.Path(a.root); errors=[]; rows=[]
 for ph in a.phases:
  p=root/'phases'/ph/'result.json'
  if not p.is_file(): errors.append(f'missing {ph} result'); continue
  d=json.loads(p.read_text()); release=d.get('release',{}); rows.append({'phase':ph,'status':d.get('status'),'sha256':sha(p),'checks':len(d.get('checks',[]))})
  if d.get('status')!='PASS': errors.append(f'{ph} not PASS')
  if release.get('gitCommit')!=a.commit: errors.append(f'{ph} commit mismatch')
  if release.get('imageDigest')!=a.image_digest: errors.append(f'{ph} image mismatch')
  if release.get('reference')!=a.reference: errors.append(f'{ph} release reference mismatch')
 out={'schemaVersion':1,'phases':rows,'passed':not errors,'errors':errors}; pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print(json.dumps({'passed':not errors,'phases':len(rows),'errors':errors})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
