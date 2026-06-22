#!/usr/bin/env python3
import argparse,datetime as dt,pathlib
from lib import load,write,ensure_fresh,now_utc,parse_time,release_identity
p=argparse.ArgumentParser(); p.add_argument('--enterprise-root',required=True); p.add_argument('--input',required=True); p.add_argument('--model',required=True); p.add_argument('--controls',required=True); p.add_argument('--policy',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); p.add_argument('--now'); p.add_argument('--score-output',required=True); p.add_argument('--controls-output',required=True); p.add_argument('--certificate-output',required=True); a=p.parse_args()
root=pathlib.Path(a.enterprise_root); inp=load(a.input); model=load(a.model); catalog=load(a.controls); policy=load(a.policy); thresholds=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(inp,thresholds['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
expected=(a.release_reference,a.git_commit,a.image_digest); phase_status={}
for letter in 'ABCDEFGHI':
    pid=f'57{letter}'; path=root/'phases'/pid/'result.json'
    if not path.is_file(): errors.append(f'missing local phase result {pid}'); continue
    d=load(path); phase_status[pid]=d.get('status')
    if d.get('status')!='PASS': errors.append(f'{pid} not PASS')
    if release_identity(d)!=expected: errors.append(f'{pid} release identity mismatch')
external_rows=inp.get('externalPhaseResults',[])
external_ids=[str(x.get('phase')) for x in external_rows]
if len(external_ids)!=len(set(external_ids)): errors.append('duplicate external phase result')
for row in external_rows:
    phase_status[str(row.get('phase'))]=row.get('status')
    if row.get('releaseReference')!=a.release_reference or row.get('gitCommit')!=a.git_commit or row.get('imageDigest')!=a.image_digest: errors.append(f"external phase identity mismatch: {row.get('phase')}")
    if not row.get('evidenceSha256') or len(row.get('evidenceSha256',''))!=64: errors.append(f"external phase evidence hash missing: {row.get('phase')}")
domains=[]; weighted=0.0; total_weight=0.0
for name,cfg in model.get('domains',{}).items():
    req=[str(x) for x in cfg.get('requiredPhases',[])]; passed=sum(phase_status.get(x)=='PASS' for x in req); score=round(100*passed/len(req),2) if req else 0
    status='PASS' if score>=thresholds['maturity']['minimumDomainScore'] else 'FAIL'
    domains.append({'domain':name,'score':score,'status':status,'requiredPhases':req,'missingOrFailed':[x for x in req if phase_status.get(x)!='PASS']})
    weighted+=score*cfg['weight']; total_weight+=cfg['weight']
    if status!='PASS': errors.append(f'domain below minimum: {name}')
overall=round(weighted/total_weight,2) if total_weight else 0
critical_snapshot=inp.get('criticalControls',{}); control_rows=[]
for cid in catalog.get('criticalControls',[]):
    status=critical_snapshot.get(cid,'NOT_RUN'); control_rows.append({'id':cid,'severity':'CRITICAL','status':status})
    if status!='PASS': errors.append(f'critical control not PASS: {cid}')
for cid in catalog.get('highControls',[]): control_rows.append({'id':cid,'severity':'HIGH','status':critical_snapshot.get(cid,'NOT_RUN')})
exceptions=inp.get('exceptions',[]); open_critical=0; open_high=0
for exc in exceptions:
    if exc.get('status')!='OPEN': continue
    sev=exc.get('severity'); open_critical+=sev=='CRITICAL'; open_high+=sev=='HIGH'
    try:
        if parse_time(exc['expiresAt'])<=now: errors.append(f"expired risk acceptance: {exc.get('id')}")
    except Exception: errors.append(f"invalid risk acceptance expiry: {exc.get('id')}")
    if len(set(exc.get('approvers',[])))<2: errors.append(f"insufficient risk acceptance approvers: {exc.get('id')}")
if open_critical>thresholds['maturity']['maximumOpenCriticalExceptions']: errors.append('open critical exceptions exceed policy')
if open_high>thresholds['maturity']['maximumOpenHighExceptions']: errors.append('open high exceptions exceed policy')
res=inp.get('resilienceCertificate',{})
if res.get('status')!='PASS' or not res.get('signatureVerified') or not res.get('evidenceSha256'): errors.append('current signed resilience certificate is required')
try:
    if parse_time(res['expiresAt'])<=now: errors.append('resilience certificate expired')
except Exception: errors.append('resilience certificate expiry invalid')
if len(set(inp.get('operationalOwners',[])))<3: errors.append('at least three distinct operational owners required')
if overall<thresholds['maturity']['minimumOverallScore']: errors.append('overall maturity score below minimum')
decision='CERTIFIED' if not errors else 'BLOCKED'; issued=now.isoformat().replace('+00:00','Z'); expires=(now+dt.timedelta(days=policy['certificateValidityDays'])).isoformat().replace('+00:00','Z')
write(a.score_output,{'schemaVersion':1,'overallScore':overall,'minimumOverallScore':thresholds['maturity']['minimumOverallScore'],'domains':domains,'decision':decision})
write(a.controls_output,{'schemaVersion':1,'decision':decision,'controls':control_rows,'openCriticalExceptions':open_critical,'openHighExceptions':open_high,'errors':errors})
write(a.certificate_output,{'schemaVersion':1,'certificateType':'SWITCHING_ENTERPRISE_PRODUCTION_MATURITY','status':'PASS' if decision=='CERTIFIED' else 'FAIL','decision':decision,'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'overallScore':overall,'issuedAt':issued,'expiresAt':expires,'operationalOwners':sorted(set(inp.get('operationalOwners',[]))),'evidenceChainRequired':True,'errors':errors})
if errors: raise SystemExit('\n'.join(errors))
