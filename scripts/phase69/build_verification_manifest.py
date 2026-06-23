#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, os
from datetime import datetime, timezone
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--evidence-root', required=True)
p.add_argument('--attestation')
p.add_argument('--output', required=True)
a=p.parse_args()
root=Path(a.evidence_root); result_dir=root/'results'
results=[]
for path in sorted(result_dir.glob('69[A-I].json')):
    results.append(json.loads(path.read_text(encoding='utf-8')))
by_phase={r['phase']:r for r in results}
expected=[f'69{x}' for x in 'ABCDEFGHI']
missing=[x for x in expected if x not in by_phase]
statuses={x:by_phase[x]['status'] for x in expected if x in by_phase}
mode='full' if results and all(r.get('mode')=='full' for r in results) else 'preflight'
attestation=None
if a.attestation and Path(a.attestation).is_file():
    attestation=json.loads(Path(a.attestation).read_text(encoding='utf-8'))
expected_commit=os.environ.get('PHASE69_GIT_SHA','unknown')
approved=bool(attestation and attestation.get('approved') is True and attestation.get('approver') and attestation.get('approvedAt'))
commit_matches=bool(attestation and expected_commit!='unknown' and attestation.get('gitCommit')==expected_commit)
if missing or any(s in {'FAIL','BLOCKED'} for s in statuses.values()): decision='BLOCKED'
elif mode!='full' or any(s!='PASS' for s in statuses.values()): decision='PREPARED'
elif not approved or not commit_matches: decision='BLOCKED'
else: decision='VERIFIED'
artifacts=[]
for path in sorted(root.rglob('*')):
    if not path.is_file() or path.resolve()==Path(a.output).resolve(): continue
    rel=str(path.relative_to(root))
    if rel in {'SHA256SUMS', 'results/69J.json'}: continue
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    artifacts.append({'path':rel,'sha256':digest,'size':path.stat().st_size})
payload={'schemaVersion':1,'phase':'69J','decision':decision,'mode':mode,
         'generatedAt':datetime.now(timezone.utc).isoformat(),'gitCommit':os.environ.get('PHASE69_GIT_SHA','unknown'),
         'missingPhases':missing,'phaseStatuses':statuses,'attestationApproved':approved,
         'attestationCommitMatches':commit_matches,'artifacts':artifacts}
out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf-8')
(root/'SHA256SUMS').write_text(''.join(f"{x['sha256']}  {x['path']}\n" for x in artifacts),encoding='utf-8')
print(decision)
