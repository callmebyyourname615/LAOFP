#!/usr/bin/env python3
import json, pathlib, sys, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2])
totals={k:0 for k in ('tests','failures','errors','skipped')}; files=[]
for p in root.rglob('TEST-*.xml'):
    try:
        e=ET.parse(p).getroot(); files.append(str(p))
        for k in totals: totals[k]+=int(float(e.attrib.get(k,0)))
    except Exception: pass
totals['reportFiles']=files
out.parent.mkdir(parents=True,exist_ok=True)
json.dump(totals,open(out,'w'),indent=2,sort_keys=True)
