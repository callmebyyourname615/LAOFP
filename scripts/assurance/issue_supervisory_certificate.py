#!/usr/bin/env python3
import argparse,yaml,hashlib
from pathlib import Path
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--assurance-root','--input','--controls','--scoring','--policy','--thresholds','--release-reference','--git-commit','--image-digest','--now','--score-output','--controls-output','--certificate-output']]; a=p.parse_args(); inp=load(a.input); policy=yaml.safe_load(open(a.policy)); scoring=yaml.safe_load(open(a.scoring)); catalog=yaml.safe_load(open(a.controls)); checks=[]; phase_scores={}
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
for p in [f'58{x}' for x in 'ABCDEFGHI']:
 f=Path(a.assurance_root)/'phases'/p/'result.json'; ok=f.exists(); d=load(f) if ok else {}; ok=ok and d.get('status')=='PASS' and identity(d.get('release',{}))==(a.release_reference,a.git_commit,a.image_digest); ck(f'{p}.pass',ok); phase_scores[p]=scoring['weights'][p] if ok else 0
for c in catalog['criticalControls']+catalog['highControls']: ck(f'control.{c}',inp.get('criticalControls',{}).get(c)=='PASS')
ck('manifest.signed',inp.get('evidenceManifest',{}).get('signatureVerified') is True and sha_ok(inp.get('evidenceManifest',{}).get('sha256')))
ck('exceptions.critical',sum(1 for x in inp.get('exceptions',[]) if x.get('severity')=='CRITICAL' and x.get('status')!='CLOSED')==0)
ck('owners',len(set(inp.get('operationalOwners',[])))>=policy['minimumOperationalOwners'])
score=sum(phase_scores.values()); status='PASS' if pass_status(checks)=='PASS' and score>=scoring['minimumScore'] else 'FAIL'; cert={'schemaVersion':1,'decision':'SUPERVISORY_READY' if status=='PASS' else 'NOT_READY','score':score,'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'issuedAt':a.now,'failedControls':[x['id'] for x in checks if x['status']=='FAIL']}; dump(a.score_output,{'schemaVersion':1,'score':score,'phaseScores':phase_scores}); dump(a.controls_output,result(status,checks)); dump(a.certificate_output,cert); raise SystemExit(0 if status=='PASS' else 1)
