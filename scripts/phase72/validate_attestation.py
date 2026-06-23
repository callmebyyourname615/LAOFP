#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--type',required=True,choices=['secret','runtime']); p.add_argument('--path',required=True); p.add_argument('--git-commit',required=True); a=p.parse_args()
try: d=json.loads(Path(a.path).read_text())
except Exception as exc: print(f'invalid attestation: {exc}'); raise SystemExit(1)
if d.get('schemaVersion')!=1 or d.get('gitCommit')!=a.git_commit: print('attestation schema/commit mismatch'); raise SystemExit(1)
if a.type=='secret':
    action_names=['credentialsGenerated','storedInVault','repositoryFrozen','historyPurged','forcePushed','ciCachesInvalidated','serviceTokensRotated','teamRecloned','leakScanClean']
    ok=all(d.get('actions',{}).get(x) is True for x in action_names) and all(d.get('approvers',{}).get(x) for x in ['secOps','repoCoordinator']) and bool(d.get('approvedAt'))
else:
    smos=['adminsProvisioned','totpVerified','passwordPolicyVerified','sessionRevocationVerified','rbacVerified','makerCheckerVerified','auditTrailVerified']
    alerts=['rulesLoaded','pendingObserved','firingObserved','resolvedObserved','routingVerified','runbookLinksVerified']
    ok=all(d.get('smos',{}).get(x) is True for x in smos) and all(d.get('alerts',{}).get(x) is True for x in alerts) and all(d.get('approvers',{}).get(x) for x in ['security','sre']) and bool(d.get('approvedAt'))
# Evidence must not contain secret material fields.
forbidden={'password','secretValue','tokenValue','privateKey','apiKeyValue'}
def walk(x):
    if isinstance(x,dict):
        for k,v in x.items():
            if k in forbidden: return False
            if not walk(v): return False
    elif isinstance(x,list):
        return all(walk(v) for v in x)
    return True
ok=ok and walk(d)
print('PASS' if ok else 'FAIL'); raise SystemExit(0 if ok else 1)
