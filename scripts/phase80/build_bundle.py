#!/usr/bin/env python3
import hashlib,json,pathlib,subprocess,sys
from datetime import datetime,timezone
root=pathlib.Path(sys.argv[1]); mode=sys.argv[2]
results=[json.load(open(p)) for p in sorted((root/'results').glob('80[A-I].json'))]
required={f'80{x}' for x in 'ABCDEFGHI'}
present={x.get('phase') for x in results}; statuses={x.get('status') for x in results}
commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
synthetic=sum(1 for x in results if x.get('synthetic'))
if mode!='full': decision='PREPARED'
elif present!=required: decision='BLOCKED'
elif statuses!={'PASS'} or synthetic: decision='NO_GO'
else:
    approvals=list((root/'artifacts').glob('*attestation.json'))
    decision='GO_PRODUCTION_CANARY' if len(approvals)>=4 else ('GO_PHASE54' if len(approvals)>=2 else 'NO_GO')
manifest=[]
for p in sorted(root.rglob('*')):
    if p.is_file() and p.name not in {'SHA256SUMS','FINAL_DECISION.json'}:
        manifest.append({'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size})
(root/'SHA256SUMS').write_text(''.join(f"{x['sha256']}  {x['path']}\n" for x in manifest))
json.dump({'decision':decision,'gitCommit':commit,'mode':mode,'syntheticEvidenceCount':synthetic,
           'controls':results,'artifacts':manifest,
           'generatedAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z')},
          open(root/'FINAL_DECISION.json','w'),indent=2,sort_keys=True)
