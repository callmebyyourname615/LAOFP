#!/usr/bin/env python3
import argparse,datetime,json,pathlib,sys,urllib.parse,urllib.request,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--prometheus-url',required=True);p.add_argument('--queries',required=True);p.add_argument('--output',required=True);p.add_argument('--timeout-seconds',type=int,default=10);a=p.parse_args();cfg=yaml.safe_load(pathlib.Path(a.queries).read_text());objectives={}
 base=a.prometheus_url.rstrip('/')
 if not base.startswith('https://') and not (base.startswith('http://127.0.0.1') or base.startswith('http://localhost')):raise SystemExit('Prometheus URL must use HTTPS outside local testing')
 for q in cfg['objectives']:
  url=base+'/api/v1/query?'+urllib.parse.urlencode({'query':q['promql']});req=urllib.request.Request(url,headers={'Accept':'application/json'})
  with urllib.request.urlopen(req,timeout=a.timeout_seconds) as r:data=json.load(r)
  if data.get('status')!='success' or len(data.get('data',{}).get('result',[]))!=1:raise SystemExit('Prometheus query did not return exactly one scalar for '+q['id'])
  value=float(data['data']['result'][0]['value'][1]);objectives[q['id']]={'achievedPercent':value}
 out={'capturedAt':datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z'),'criticalIncidentOpen':False,'objectives':objectives};pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
if __name__=='__main__':sys.exit(main())
