#!/usr/bin/env python3
import argparse, json, xml.etree.ElementTree as ET
from pathlib import Path

def junit(path):
    root=ET.parse(path).getroot(); suites=[root] if root.tag=='testsuite' else list(root.findall('testsuite'))
    vals={k:sum(int(s.attrib.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    vals['status']='PASS' if vals['failures']==0 and vals['errors']==0 and vals['tests']>0 else 'FAIL'; return vals

def k6(path):
    d=json.loads(Path(path).read_text()); m=d.get('metrics',{})
    p95=m.get('http_req_duration',{}).get('values',{}).get('p(95)')
    er=m.get('http_req_failed',{}).get('values',{}).get('rate')
    return {'status':'PASS' if p95 is not None and er is not None else 'FAIL','p95_ms':p95,'error_rate':er}

p=argparse.ArgumentParser(); p.add_argument('--type',choices=['junit','k6','json','shell'],required=True)
p.add_argument('--input',required=True); p.add_argument('--output',required=True); p.add_argument('--control-id',required=True)
p.add_argument('--environment',default='uat'); p.add_argument('--git-commit',default='unknown'); p.add_argument('--synthetic',action='store_true')
a=p.parse_args()
if a.type=='junit': observed=junit(a.input)
elif a.type=='k6': observed=k6(a.input)
elif a.type=='json': observed=json.loads(Path(a.input).read_text())
else:
    code=int(Path(a.input).read_text().strip()); observed={'exit_code':code,'status':'PASS' if code==0 else 'FAIL'}
out={'control_id':a.control_id,'status':observed.pop('status','PASS'),'environment':a.environment,
     'git_commit':a.git_commit,'synthetic':a.synthetic,'observed':observed,'threshold':{},'source':a.input}
Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(out,indent=2)+'\n')
