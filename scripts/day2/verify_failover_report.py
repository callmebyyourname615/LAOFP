#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--report',required=True);p.add_argument('--policy',required=True);p.add_argument('--mode',choices=['failover','failback'],required=True);p.add_argument('--output',required=True);a=p.parse_args();r=json.loads(pathlib.Path(a.report).read_text());pol=yaml.safe_load(pathlib.Path(a.policy).read_text());checks=[]
 def add(i,ok,av,ev):checks.append({'id':i,'status':'PASS' if ok else 'FAIL','actual':av,'expected':ev})
 add(a.mode+'-status',r.get('status')=='PASS',r.get('status'),'PASS');add(a.mode+'-rto',r.get('rtoSeconds',10**9)<=pol['postgresql']['rtoSeconds'],r.get('rtoSeconds'),pol['postgresql']['rtoSeconds']);add(a.mode+'-rpo',r.get('rpoSeconds',10**9)<=pol['postgresql']['rpoSeconds'],r.get('rpoSeconds'),pol['postgresql']['rpoSeconds']);add(a.mode+'-data-loss',r.get('dataLossCount',1)==0,r.get('dataLossCount'),0);add(a.mode+'-duplicate-replay',r.get('duplicateReplayCount',1)==0,r.get('duplicateReplayCount'),0);add(a.mode+'-reconciliation',r.get('reconciliationStatus')=='PASS',r.get('reconciliationStatus'),'PASS');add(a.mode+'-single-primary',r.get('primaryCountAfter')==1,r.get('primaryCountAfter'),1)
 if a.mode=='failback':add('failback-approved',r.get('approved') is True,r.get('approved'),True)
 status='PASS' if all(x['status']=='PASS' for x in checks) else 'FAIL';pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'mode':a.mode,'status':status,'checks':checks},indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
