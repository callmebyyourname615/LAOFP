#!/usr/bin/env python3
import argparse, datetime as dt, decimal, hashlib, json, math
from pathlib import Path
D=decimal.Decimal

def parse_time(value): return dt.datetime.fromisoformat(value.replace('Z','+00:00'))
def forecast(rows,hours,safe_per_replica):
    if len(rows)<6: raise ValueError('at least six observations are required')
    rows=sorted(rows,key=lambda r:parse_time(r['observed_at']))
    origin=parse_time(rows[0]['observed_at'])
    xs=[(parse_time(r['observed_at'])-origin).total_seconds()/3600 for r in rows]
    ys=[float(r['request_rate']) for r in rows]; n=len(rows)
    sx=sum(xs); sy=sum(ys); sxy=sum(x*y for x,y in zip(xs,ys)); sxx=sum(x*x for x in xs)
    denominator=n*sxx-sx*sx
    slope=0 if abs(denominator)<1e-12 else (n*sxy-sx*sy)/denominator
    intercept=(sy-slope*sx)/n
    rate=max(0,intercept+slope*(xs[-1]+hours)); upper=rate*1.20; lower=max(0,rate*0.85)
    replicas=max(1,math.ceil(upper/float(safe_per_replica)))
    return {'forecast_request_rate':round(rate,4),'confidence_lower':round(lower,4),'confidence_upper':round(upper,4),'required_replicas':replicas,'slope_per_hour':round(slope,8)}
def main(inp,out,horizon,safe):
    doc=json.loads(Path(inp).read_text(encoding='utf-8')); rows=doc.get('observations') or []
    result=forecast(rows,horizon*24,D(safe)); result.update({'component':doc.get('component'),'environment':doc.get('environment'),'horizon_days':horizon,'model_version':'linear-v1'})
    canonical=json.dumps(result,sort_keys=True,separators=(',',':')); result['evidence_sha256']=hashlib.sha256(canonical.encode()).hexdigest()
    Path(out).write_text(json.dumps(result,sort_keys=True,indent=2)+'\n',encoding='utf-8'); print(json.dumps(result,sort_keys=True))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('input');p.add_argument('output');p.add_argument('--horizon-days',type=int,default=30);p.add_argument('--safe-request-rate-per-replica',default='500')
    a=p.parse_args();main(a.input,a.output,a.horizon_days,a.safe_request_rate_per_replica)
