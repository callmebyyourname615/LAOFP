#!/usr/bin/env python3
import argparse, json
from datetime import datetime, timezone
from pathlib import Path
import yaml

def parse_time(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))

p=argparse.ArgumentParser()
p.add_argument('--policy',required=True)
p.add_argument('--results-dir',required=True)
p.add_argument('--approvals',required=True)
p.add_argument('--ledger',required=True)
p.add_argument('--output',required=True)
p.add_argument('--git-commit',required=True)
p.add_argument('--image-digest',default='unknown')
p.add_argument('--canary',action='store_true')
a=p.parse_args()
policy=yaml.safe_load(Path(a.policy).read_text())
results=[]
for f in sorted(Path(a.results_dir).glob('*.json')):
    try: results.append(json.loads(f.read_text()))
    except json.JSONDecodeError: pass
by={r.get('control_id') or r.get('phase'):r for r in results}
missing=[]; failures=[]; blockers=[]
for cid in policy['required_controls']:
    r=by.get(cid)
    if not r:
        missing.append(cid); continue
    if r.get('status')!='PASS': failures.append(f'failed:{cid}')
    if r.get('synthetic',False): failures.append(f'synthetic:{cid}')
    if r.get('git_commit') not in (None,a.git_commit): failures.append(f'commit:{cid}')
ledger=json.loads(Path(a.ledger).read_text())
approvals=json.loads(Path(a.approvals).read_text())
now=datetime.now(timezone.utc); valid_approvers=set()
for role in policy['required_approval_roles']:
    valid=[]
    for item in approvals.get('approvals',[]):
        expires=parse_time(item.get('expires_at'))
        if (item.get('role')==role and item.get('status')=='APPROVED'
                and item.get('git_commit')==a.git_commit
                and item.get('evidence_tail_hash')==ledger.get('tail_hash')
                and expires is not None and expires>now):
            valid.append(item)
    if not valid: blockers.append(f'approval:{role}')
    else: valid_approvers.add(valid[0].get('approver'))
if len(valid_approvers) < policy.get('minimum_unique_approvers',1):
    blockers.append(f'unique_approvers:{len(valid_approvers)}')
if missing: decision='PREPARED'
elif failures: decision='NO_GO'
elif blockers: decision='BLOCKED'
else: decision='CANARY_GO' if a.canary else 'GO'
out={'decision':decision,'git_commit':a.git_commit,'image_digest':a.image_digest,
     'missing_controls':missing,'failures':failures,'blockers':blockers,
     'valid_unique_approvers':len(valid_approvers),'evaluated_at':now.isoformat(),'synthetic':False}
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
Path(a.output).write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps(out))
raise SystemExit(0 if decision in ('GO','CANARY_GO','PREPARED') else 1)
