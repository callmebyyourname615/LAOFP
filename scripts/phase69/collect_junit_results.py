#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--root', default='target')
p.add_argument('--output', required=True)
a=p.parse_args()
root=Path(a.root)
files=sorted({*root.glob('surefire-reports/TEST-*.xml'), *root.glob('failsafe-reports/TEST-*.xml')})
summary={'files':len(files),'tests':0,'failures':0,'errors':0,'skipped':0,'reports':[]}
for file in files:
    tree=ET.parse(file); suite=tree.getroot()
    entry={'file':str(file),'name':suite.attrib.get('name',''),
           'tests':int(float(suite.attrib.get('tests','0'))),
           'failures':int(float(suite.attrib.get('failures','0'))),
           'errors':int(float(suite.attrib.get('errors','0'))),
           'skipped':int(float(suite.attrib.get('skipped','0')))}
    summary['reports'].append(entry)
    for k in ('tests','failures','errors','skipped'): summary[k]+=entry[k]
summary['passed']=summary['tests']-summary['failures']-summary['errors']-summary['skipped']
summary['status']='PASS' if files and summary['failures']==0 and summary['errors']==0 else 'FAIL'
out=Path(a.output); out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(summary,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print(json.dumps(summary, sort_keys=True))
raise SystemExit(0 if summary['status']=='PASS' else 1)
