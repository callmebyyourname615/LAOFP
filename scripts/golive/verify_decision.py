#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, pathlib

def sha256(path: pathlib.Path) -> str:
    h=hashlib.sha256(); h.update(path.read_bytes()); return h.hexdigest()

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--decision',required=True); ap.add_argument('--stage',required=True)
    ap.add_argument('--reference',required=True); ap.add_argument('--rc-id',required=True)
    ap.add_argument('--git-commit',required=True); ap.add_argument('--application-digest',required=True)
    ap.add_argument('--evidence',required=True); ap.add_argument('--max-age-seconds',type=int,default=900)
    ap.add_argument('--allowed-decision',default='PROMOTE'); ap.add_argument('--output')
    a=ap.parse_args(); p=pathlib.Path(a.decision); d=json.loads(p.read_text())
    errors=[]
    expected={'stage':str(a.stage),'releaseReference':a.reference,'releaseCandidateId':a.rc_id,'gitCommit':a.git_commit,'applicationImageDigest':a.application_digest}
    for k,v in expected.items():
        if str(d.get(k))!=v: errors.append(f'{k} mismatch')
    if d.get('decision')!=a.allowed_decision: errors.append('decision is not allowed')
    approvers={str(x).strip() for x in d.get('approvedBy',[]) if str(x).strip()}
    if len(approvers)<2: errors.append('two distinct approvers required')
    try:
        issued=dt.datetime.fromisoformat(str(d.get('issuedAt','')).replace('Z','+00:00'))
        age=(dt.datetime.now(dt.timezone.utc)-issued).total_seconds()
        if age<0 or age>a.max_age_seconds: errors.append('decision is stale or from the future')
    except Exception: errors.append('invalid issuedAt')
    ev=pathlib.Path(a.evidence)
    if not ev.is_file() or ev.is_symlink(): errors.append('evidence file missing')
    elif d.get('evidenceSha256')!=sha256(ev): errors.append('evidence digest mismatch')
    report={'schemaVersion':1,'verified':not errors,'stage':str(a.stage),'decision':d.get('decision'),'approverCount':len(approvers),'errors':errors}
    text=json.dumps(report,indent=2,sort_keys=True)+'\n'
    if a.output: pathlib.Path(a.output).write_text(text)
    print(text,end=''); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
