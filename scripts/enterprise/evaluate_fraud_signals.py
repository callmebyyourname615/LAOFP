#!/usr/bin/env python3
import argparse
from lib import load,write,ensure_fresh,now_utc,compare
p=argparse.ArgumentParser(); p.add_argument('--snapshot',required=True); p.add_argument('--rules',required=True); p.add_argument('--scoring',required=True); p.add_argument('--routing',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--report-output',required=True); p.add_argument('--routing-output',required=True); a=p.parse_args()
s=load(a.snapshot); rules=load(a.rules); scoring=load(a.scoring); routing=load(a.routing); th=load(a.thresholds); now=now_utc(a.now); errors=[]
try: ensure_fresh(s,th['freshness']['defaultSnapshotMaxAgeSeconds'],now)
except Exception as e: errors.append(str(e))
entities=[]; unassigned_critical=0; overdue_high=0
for entity in s.get('entities',[]):
    score=0; reasons=[]; triggered=[]
    signals=entity.get('signals',{})
    for rule in rules.get('rules',[]):
        value=signals.get(rule['signal'],0)
        if not isinstance(value,(int,float)): errors.append(f"invalid signal {rule['signal']} for entity {entity.get('entityId')}"); continue
        if compare(value,rule['operator'],rule['threshold']):
            score=min(100,score+rule['score']); reasons.append(rule['reasonCode']); triggered.append({'ruleId':rule['id'],'signal':rule['signal'],'value':value,'contribution':rule['score']})
    action=next(x for x in scoring.get('actions',[]) if score>=x['minimumScore'])
    severity='CRITICAL' if score>=th['fraud']['criticalRiskScore'] else 'HIGH' if score>=th['fraud']['highRiskScore'] else 'MEDIUM' if score>=40 else 'LOW'
    route=None
    for reason in reasons:
        route=routing.get('routes',{}).get(reason) or route
    assigned=bool(entity.get('caseOwner')) if action['action'] in ('AUTO_HOLD_AND_CASE','CASE_AND_ENHANCED_MONITORING') else True
    if severity=='CRITICAL' and not assigned: unassigned_critical+=1
    if severity=='HIGH' and entity.get('caseOverdue',False): overdue_high+=1
    entities.append({'entityId':entity.get('entityId'),'score':score,'severity':severity,'action':action['action'],'humanReviewRequired':action['humanReviewRequired'],'reasonCodes':sorted(set(reasons)),'triggeredRules':triggered,'route':route,'caseAssigned':assigned})
if unassigned_critical>th['fraud']['maximumUnassignedCriticalCases']: errors.append('unassigned critical fraud cases exceed threshold')
if overdue_high>th['fraud']['maximumOverdueHighRiskCases']: errors.append('overdue high-risk cases exceed threshold')
if not s.get('modelVersion'): errors.append('model/rule version is required')
status='PASS' if not errors else 'FAIL'
write(a.report_output,{'schemaVersion':1,'status':status,'modelVersion':s.get('modelVersion'),'entities':entities,'errors':errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.routing_output,{'schemaVersion':1,'status':status,'criticalUnassigned':unassigned_critical,'highRiskOverdue':overdue_high,'routes':[{'entityId':x['entityId'],'route':x['route'],'action':x['action']} for x in entities if x['action']!='ALLOW']})
if errors: raise SystemExit('\n'.join(errors))
