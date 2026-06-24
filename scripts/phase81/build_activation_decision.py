#!/usr/bin/env python3
import json,pathlib,sys
root=pathlib.Path(sys.argv[1]); phase80=sys.argv[2]; mode=sys.argv[3]
results=[json.load(open(p)) for p in sorted((root/'results').glob('81[A-I].json'))]
if mode!='full': decision='PREPARED'
elif len(results)!=9 or any(x['status']!='PASS' for x in results): decision='BLOCKED'
elif not phase80 or not pathlib.Path(phase80).is_file(): decision='BLOCKED_PHASE80'
else:
    d=json.load(open(phase80))
    decision='BAU_ACTIVE' if d.get('decision')=='GO_PRODUCTION_CANARY' and d.get('syntheticEvidenceCount')==0 else 'BLOCKED_PHASE80'
json.dump({'decision':decision,'controls':results,'dashboardsOperational':len(results)>=4,
 'continuousAssuranceRequested':decision=='BAU_ACTIVE'},open(root/'FINAL_DECISION.json','w'),indent=2,sort_keys=True)
