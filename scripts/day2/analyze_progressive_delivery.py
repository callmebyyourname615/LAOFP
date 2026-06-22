#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--snapshot',required=True);p.add_argument('--thresholds',required=True);p.add_argument('--output',required=True);a=p.parse_args();s=json.loads(pathlib.Path(a.snapshot).read_text());t=yaml.safe_load(pathlib.Path(a.thresholds).read_text())['progressiveDelivery'];checks=[]
 def add(i,ok,av,ev):checks.append({'id':i,'status':'PASS' if ok else 'FAIL','actual':av,'expected':ev})
 add('error-rate',s['errorRatePercent']<=t['maximumErrorRatePercent'],s['errorRatePercent'],t['maximumErrorRatePercent']);add('p95-latency',s['p95LatencyMs']<=t['maximumP95LatencyMs'],s['p95LatencyMs'],t['maximumP95LatencyMs']);add('p99-latency',s['p99LatencyMs']<=t['maximumP99LatencyMs'],s['p99LatencyMs'],t['maximumP99LatencyMs']);add('observation-window',s['observationSeconds']>=t['minimumObservationSeconds'],s['observationSeconds'],t['minimumObservationSeconds']);add('critical-alerts',s['criticalAlerts']==0,s['criticalAlerts'],0);add('reconciliation',s['reconciliationStatus']=='PASS',s['reconciliationStatus'],'PASS');add('error-budget',s['errorBudgetDecision']=='ALLOW',s['errorBudgetDecision'],'ALLOW')
 status='PROMOTE' if all(c['status']=='PASS' for c in checks) else 'ROLLBACK';pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'decision':status,'checks':checks},indent=2,sort_keys=True)+'\n');return 0 if status=='PROMOTE' else 1
if __name__=='__main__':sys.exit(main())
