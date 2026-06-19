#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, pathlib, re
try: import yaml
except ImportError as exc: raise SystemExit('PyYAML is required') from exc

def digest(p:pathlib.Path)->str:
    h=hashlib.sha256();
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''): h.update(b)
    return h.hexdigest()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--root',required=True); ap.add_argument('--plan',default='config/phase55-golive-plan.yaml')
    ap.add_argument('--reference',required=True); ap.add_argument('--rc-id',required=True); ap.add_argument('--git-commit',required=True)
    ap.add_argument('--application-digest',required=True); ap.add_argument('--migration-digest',required=True); ap.add_argument('--output',required=True)
    a=ap.parse_args(); root=pathlib.Path(a.root).resolve(); plan=yaml.safe_load(pathlib.Path(a.plan).read_text())
    phases=[]; ready=True
    for spec in plan['phases']:
        rp=root/'phases'/spec['id']/'result.json'; missing=[]; status='NOT_RUN'
        if rp.is_file():
            data=json.loads(rp.read_text()); status=data.get('status','FAIL'); rel=data.get('release',{})
            expected={'reference':a.reference,'releaseCandidateId':a.rc_id,'gitCommit':a.git_commit,'applicationImageDigest':a.application_digest,'migrationImageDigest':a.migration_digest}
            if any(rel.get(k)!=v for k,v in expected.items()): raise SystemExit(f"release identity mismatch in {spec['id']}")
        generated_outputs={'operational-acceptance/manifest.json','operational-acceptance/checksums.sha256'}
        for relpath in spec.get('evidence',[]):
            if relpath in generated_outputs:
                continue
            p=(root/relpath).resolve()
            try: p.relative_to(root)
            except ValueError: raise SystemExit('evidence path escapes root')
            if not p.is_file() or p.is_symlink(): missing.append(relpath)
        if status=='PASS' and missing: status='FAIL'
        if spec.get('requiredForOperationalAcceptance') and status!='PASS': ready=False
        phases.append({'id':spec['id'],'name':spec['name'],'status':status,'missingEvidence':missing})
    artifacts=[]; out=pathlib.Path(a.output).resolve(); excluded={out,out.with_name('checksums.sha256')}
    for p in sorted(root.rglob('*')):
        if p.is_file() and not p.is_symlink() and p not in excluded:
            artifacts.append({'path':p.relative_to(root).as_posix(),'size':p.stat().st_size,'sha256':digest(p)})
    doc={'schemaVersion':1,'generatedAt':dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00','Z'),'release':{'reference':a.reference,'releaseCandidateId':a.rc_id,'gitCommit':a.git_commit,'applicationImageDigest':a.application_digest,'migrationImageDigest':a.migration_digest},'phases':phases,'artifacts':artifacts,'operationallyAccepted':ready}
    out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    out.with_name('checksums.sha256').write_text(f"{digest(out)}  {out.name}\n")
    print(json.dumps({'operationallyAccepted':ready,'phases':len(phases),'artifacts':len(artifacts)})); return 0 if ready else 3
if __name__=='__main__': raise SystemExit(main())
