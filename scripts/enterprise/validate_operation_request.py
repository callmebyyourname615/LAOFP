#!/usr/bin/env python3
import argparse,re
from lib import load,write,now_utc,parse_time,safe_relpath
p=argparse.ArgumentParser(); p.add_argument('--request',required=True); p.add_argument('--catalog',required=True); p.add_argument('--authorization',required=True); p.add_argument('--approval-policy',required=True); p.add_argument('--safety-limits',required=True); p.add_argument('--now'); p.add_argument('--validation-output',required=True); p.add_argument('--plan-output',required=True); a=p.parse_args()
req=load(a.request); catalog=load(a.catalog).get('operations',{}); auth=load(a.authorization); approvals_policy=load(a.approval_policy); limits=load(a.safety_limits); now=now_utc(a.now); errors=[]
op=req.get('operation'); cfg=catalog.get(op)
if not cfg: errors.append('operation not in allowlisted catalog'); cfg={}
role=req.get('requesterRole'); allowed=auth.get('roles',{}).get(role,[])
if op not in allowed: errors.append('requester role is not authorized for operation')
requester=req.get('requester'); approvers=req.get('approvers',[])
if not requester: errors.append('requester is required')
if len(set(approvers))<cfg.get('approvalCount',99): errors.append('insufficient distinct approvers')
if auth.get('separationOfDuties',{}).get('requesterCannotBeSoleApprover') and requester in approvers and len(set(approvers))<=1: errors.append('requester cannot be sole approver')
if cfg.get('risk')=='critical' and requester in approvers: errors.append('critical requester cannot approve')
records=req.get('approvalRecords',[])
if len({x.get('approver') for x in records if x.get('approver')})<cfg.get('approvalCount',99): errors.append('signed approval records do not satisfy required approval count')
if set(approvers)!={x.get('approver') for x in records if x.get('approver')}: errors.append('approver list does not match signed approval records')
for approval in records:
    if not approval.get('signatureVerified') or not approval.get('evidenceSha256') or len(approval.get('evidenceSha256',''))!=64: errors.append('approval signature/evidence binding missing')
    try:
        age=(now-parse_time(approval['approvedAt'])).total_seconds()/60
        if age<0 or age>approvals_policy['approvalMaximumAgeMinutes']: errors.append('approval is stale')
    except Exception: errors.append('approval timestamp invalid')
params=req.get('parameters',{})
target=params.get('target')
if target and cfg.get('allowedTargetPattern') and not re.fullmatch(cfg['allowedTargetPattern'],target): errors.append('target violates allowlist pattern')
if op=='scale-application':
    replicas=params.get('replicas')
    if not isinstance(replicas,int) or not limits['minimumScaleReplicas']<=replicas<=limits['maximumScaleReplicas']: errors.append('replica count outside safety limits')
if op=='retry-dead-letter':
    count=params.get('recordCount')
    if not isinstance(count,int) or count<1 or count>limits['maximumDeadLetterRetryBatch']: errors.append('dead-letter retry batch outside safety limits')
if cfg.get('dryRunRequired') and req.get('dryRunStatus')!='PASS': errors.append('successful dry-run evidence required')
if req.get('namespace') not in limits.get('productionNamespaceAllowlist',[]): errors.append('namespace not allowlisted')
for v in params.values():
    if isinstance(v,str) and (';' in v or '&&' in v or '|' in v or '$(' in v or '`' in v): errors.append('shell metacharacters prohibited')
status='PASS' if not errors else 'FAIL'
plan={'schemaVersion':1,'status':status,'operation':op,'risk':cfg.get('risk'),'requester':requester,'parameters':params,'maximumScope':cfg.get('maximumScope'),'commandTemplate':cfg.get('commandTemplate') if status=='PASS' else None,'executionAuthorized':False,'requiresSeparateExecutor':True,'evaluatedAt':now.isoformat().replace('+00:00','Z')}
write(a.validation_output,{'schemaVersion':1,'status':status,'errors':errors,'approverCount':len(set(approvers)),'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.plan_output,plan)
if errors: raise SystemExit('\n'.join(errors))
