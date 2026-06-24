#!/usr/bin/env python3
import json,math,sys
src,out=sys.argv[1:]; data=json.load(open(src)); recommendations=[]
for p in data.get('participants',[]):
    code=p['participant']; p95=float(p.get('p95Tps',0)); peak=float(p.get('peakTps',p95))
    sustained=max(1,math.ceil(p95*1.25)); burst=max(sustained,math.ceil(peak*1.20))
    recommendations.append({'participant':code,'sustainedTps':sustained,'burstCapacity':burst,
      'refillPerSecond':sustained,'temporary':False,'requiresMakerChecker':True})
json.dump({'recommendations':recommendations,'sourceSynthetic':bool(data.get('synthetic',True))},open(out,'w'),indent=2,sort_keys=True)
