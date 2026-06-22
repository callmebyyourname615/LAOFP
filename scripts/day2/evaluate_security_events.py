#!/usr/bin/env python3
import argparse,collections,datetime,json,pathlib,sys,yaml

def parse(ts):
 if not ts:return None
 return datetime.datetime.fromisoformat(ts.replace('Z','+00:00'))
def main():
 p=argparse.ArgumentParser();p.add_argument('--events',required=True);p.add_argument('--catalog-dir',required=True);p.add_argument('--output',required=True);a=p.parse_args();events=json.loads(pathlib.Path(a.events).read_text());rules=[]
 for f in sorted(pathlib.Path(a.catalog_dir).glob('*.yaml')):rules.extend(yaml.safe_load(f.read_text()).get('rules',[]))
 captured=parse(events.get('capturedAt'))
 if captured is None: raise SystemExit('capturedAt is required')
 detections=[]
 for r in rules:
  cutoff=captured-datetime.timedelta(seconds=int(r['windowSeconds'])); matched=[]
  for e in events.get('events',[]):
   if e.get('eventType')!=r['eventType']:continue
   at=parse(e.get('occurredAt'))
   if at is None or at>captured or at<cutoff:continue
   matched.append(e)
  count=len(matched);triggered=count>=int(r['threshold']);detections.append({'id':r['id'],'severity':r['severity'],'eventCount':count,'threshold':r['threshold'],'windowSeconds':r['windowSeconds'],'triggered':triggered,'runbook':r['runbook']})
 blocking=[d for d in detections if d['triggered'] and d['severity'] in ('critical','high')];status='FAIL' if blocking else 'PASS';out={'schemaVersion':1,'status':status,'capturedAt':events.get('capturedAt'),'detections':detections,'blockingDetectionCount':len(blocking)};pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
