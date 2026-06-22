#!/usr/bin/env python3
import argparse,yaml
from lib import *
p=argparse.ArgumentParser(); [p.add_argument(x,required=True) for x in ['--snapshot','--lifecycle-policy','--change-policy','--code-set-policy','--thresholds','--now','--report-output','--decision-output']]; a=p.parse_args(); s=load(a.snapshot); pol=yaml.safe_load(open(a.lifecycle_policy)); checks=[]
def ck(i,o,d=''): checks.append({'id':i,'status':'PASS' if o else 'FAIL','detail':d})
ck('snapshot.fresh',age_minutes(s['capturedAt'],a.now)<=yaml.safe_load(open(a.thresholds))['snapshotMaxAgeMinutes']); packs={x['messagePack']:x for x in s.get('messagePacks',[])}
for m in pol['supportedMessagePacks']:
 x=packs.get(m); ck(f'{m}.present',bool(x));
 if not x: continue
 for k in ['schemaValidation','marketPracticeValidation','negativeTests','backwardCompatibility','signatureVerification']: ck(f'{m}.{k}',x.get(k)=='PASS')
 ck(f'{m}.digest',sha_ok(x.get('packSha256')))
cs=s.get('externalCodeSets',{}); ck('codeSets.fresh',float(cs.get('ageHours',999))<=pol['externalCodeSetFreshnessHours']); ck('codeSets.atomic',cs.get('activationMode')=='ATOMIC'); ck('codeSets.partialSafe',cs.get('partialDownloadReplacedActive') is False)
status=pass_status(checks); dump(a.report_output,result(status,checks,messagePackCount=len(packs))); dump(a.decision_output,{'schemaVersion':1,'decision':'MESSAGE_LIFECYCLE_COMPATIBLE' if status=='PASS' else 'BLOCK_MESSAGE_PACK_ACTIVATION','failedControls':[x['id'] for x in checks if x['status']=='FAIL']}); raise SystemExit(0 if status=='PASS' else 1)
