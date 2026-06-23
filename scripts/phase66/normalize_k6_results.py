#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, tempfile
from pathlib import Path
import yaml

def val(metric,key,default=None):
    return metric.get('values',{}).get(key,default)
def main():
    p=argparse.ArgumentParser(); p.add_argument('--thresholds'); p.add_argument('--input-dir'); p.add_argument('--output'); p.add_argument('--self-test',action='store_true'); a=p.parse_args()
    if a.self_test:
        with tempfile.TemporaryDirectory() as d:
            q=Path(d); sample={'metrics':{'http_req_duration':{'values':{'p(95)':100}},'http_req_failed':{'values':{'rate':0}},'http_reqs':{'values':{'rate':10}}}}
            (q/'smoke.json').write_text(json.dumps(sample)); assert json.loads((q/'smoke.json').read_text())['metrics']
        return 0
    cfg=yaml.safe_load(Path(a.thresholds).read_text()); results=[]
    for scenario,limit in cfg['scenarios'].items():
        path=Path(a.input_dir)/f'{scenario}.json'; row={'scenario':scenario,'status':'FAIL','violations':[]}
        if not path.exists(): row['violations'].append('missing summary export')
        else:
            data=json.loads(path.read_text()); m=data.get('metrics',{})
            p95=val(m.get('http_req_duration',{}),'p(95)'); err=val(m.get('http_req_failed',{}),'rate'); rate=val(m.get('http_reqs',{}),'rate')
            row.update(p95Ms=p95,errorRate=err,requestRate=rate)
            if p95 is None or p95>limit['maxP95Ms']: row['violations'].append('p95 threshold')
            if err is None or err>limit['maxErrorRate']: row['violations'].append('error-rate threshold')
            if limit.get('minRequestRate') is not None and (rate is None or rate<limit['minRequestRate']): row['violations'].append('request-rate threshold')
            row['status']='PASS' if not row['violations'] else 'FAIL'
        results.append(row)
    doc={'schemaVersion':1,'results':results,'passed':all(r['status']=='PASS' for r in results)}
    Path(a.output).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n')
    return 0 if doc['passed'] else 2
if __name__=='__main__': raise SystemExit(main())
