#!/usr/bin/env python3
import argparse
from lib import load,write,ensure_fresh,now_utc
p=argparse.ArgumentParser(); p.add_argument('--inventory',required=True); p.add_argument('--deletion-request',required=True); p.add_argument('--retention-policy',required=True); p.add_argument('--deletion-policy',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--report-output',required=True); p.add_argument('--eligibility-output',required=True); a=p.parse_args()
inv=load(a.inventory); req=load(a.deletion_request); retention=load(a.retention_policy); deletion=load(a.deletion_policy); th=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(inv,th['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
datasets={x.get('dataset'):x for x in inv.get('datasets',[])}; policy=retention.get('datasets',{}); rows=[]
for name,cfg in policy.items():
    row=datasets.get(name); item_errors=[]
    if not row: item_errors.append('inventory missing')
    else:
        if row.get('classification')!=cfg.get('classification'): item_errors.append('classification mismatch')
        if row.get('retentionDays')!=cfg.get('retentionDays'): item_errors.append('retention mismatch')
        if not row.get('encryptedAtRest'): item_errors.append('encryption not verified')
        if row.get('legalHoldCount',0)>0 and row.get('purgeEligibleCount',0)>0: item_errors.append('held data marked purge eligible')
        if row.get('archiveObjectCount',0)>0 and not row.get('archiveChecksumVerified'): item_errors.append('archive checksum not verified')
    rows.append({'dataset':name,'status':'PASS' if not item_errors else 'FAIL','errors':item_errors})
    errors.extend(f'{name}: {e}' for e in item_errors)
req_errors=[]
mode=req.get('mode')
if mode not in deletion.get('allowedModes',[]): req_errors.append('unsupported deletion mode')
if not req.get('requestId') or not req.get('dataset'): req_errors.append('requestId and dataset are required')
if req.get('dataset') not in policy: req_errors.append('dataset is not governed by retention policy')
if not req.get('retentionExpired'): req_errors.append('retention has not expired')
if req.get('legalHoldPresent'): req_errors.append('legal hold blocks deletion')
if req.get('referentialIntegrityStatus')!='PASS': req_errors.append('referential integrity not PASS')
if req.get('archiveVerificationStatus')!='PASS': req_errors.append('archive verification not PASS')
if not req.get('deletionManifestSha256') or len(req['deletionManifestSha256'])!=64: req_errors.append('deletion manifest hash missing')
approvers=req.get('approvers',[])
if len(set(approvers))<retention.get('minimumApproversForDeletion',2): req_errors.append('insufficient distinct approvers')
if mode=='EXECUTE' and req.get('executionConfirmation')!=deletion.get('executeConfirmation'): req_errors.append('execute confirmation missing')
if mode=='EXECUTE' and not req.get('dryRunEvidenceSha256'): req_errors.append('execute mode requires prior dry-run evidence')
status='PASS' if not errors else 'FAIL'; eligible=not req_errors
write(a.report_output,{'schemaVersion':1,'status':status,'datasets':rows,'errors':errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.eligibility_output,{'schemaVersion':1,'status':'ELIGIBLE' if eligible else 'BLOCKED','mode':mode,'requestId':req.get('requestId'),'dataset':req.get('dataset'),'errors':req_errors,'destructiveActionExecuted':False,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
if errors or req_errors: raise SystemExit('\n'.join(errors+req_errors))
