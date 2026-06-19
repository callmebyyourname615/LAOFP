#!/usr/bin/env python3
import json, pathlib, sys
p=pathlib.Path(sys.argv[1]); data=json.loads(p.read_text()); metrics=data.get('metrics', {})
def value(name,key): return metrics.get(name,{}).get('values',{}).get(key)
report={'source':str(p),'checksRate':value('checks','rate'),'failureRate':value('http_req_failed','rate'),'requests':value('http_reqs','count'),'requestRate':value('http_reqs','rate'),'p50Ms':value('http_req_duration','med'),'p95Ms':value('http_req_duration','p(95)'),'p99Ms':value('http_req_duration','p(99)'),'droppedIterations':value('dropped_iterations','count') or 0}
report['passed']=report['failureRate'] is not None and report['failureRate'] < .01 and report['droppedIterations'] == 0
out=p.with_suffix('.report.json'); out.write_text(json.dumps(report,indent=2)+'\n'); print(json.dumps(report,indent=2))
if not report['passed']: sys.exit(1)
