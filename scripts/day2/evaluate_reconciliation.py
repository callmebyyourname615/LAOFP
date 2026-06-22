#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml,hashlib

def get_path(d,path):
 for p in path.split('.'): d=d[p]
 return d

def main():
 p=argparse.ArgumentParser();p.add_argument('--snapshot',required=True);p.add_argument('--rules',required=True);p.add_argument('--thresholds',required=True);p.add_argument('--output',required=True);p.add_argument('--now');a=p.parse_args()
 s=json.loads(pathlib.Path(a.snapshot).read_text()); import datetime; captured=datetime.datetime.fromisoformat(s['capturedAt'].replace('Z','+00:00')); now=datetime.datetime.fromisoformat(a.now.replace('Z','+00:00')) if a.now else datetime.datetime.now(datetime.timezone.utc); age=(now-captured).total_seconds(); rules=yaml.safe_load(pathlib.Path(a.rules).read_text()); t=yaml.safe_load(pathlib.Path(a.thresholds).read_text()); checks=[]
 for r in rules['rules']:
  actual=s.get(r['field']); expected=r['expected'] if 'expected' in r else get_path(t,r['expectedFromThreshold'])
  op=r['operator']; ok=(actual==expected if op=='eq' else actual<=expected if op=='lte' else actual>=expected)
  checks.append({'id':r['id'],'actual':actual,'operator':op,'expected':expected,'severity':r['severity'],'status':'PASS' if ok else 'FAIL'})
 checks.append({'id':'snapshot-freshness','actual':age,'operator':'lte','expected':t['reconciliation']['maximumReportAgeSeconds'],'severity':'high','status':'PASS' if 0<=age<=t['reconciliation']['maximumReportAgeSeconds'] else 'FAIL'}); status='PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL'; report={'schemaVersion':1,'status':status,'capturedAt':s.get('capturedAt'),'dataAgeSeconds':age,'snapshotSha256':hashlib.sha256(pathlib.Path(a.snapshot).read_bytes()).hexdigest(),'checks':checks}
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2,sort_keys=True)+'\n'); return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
