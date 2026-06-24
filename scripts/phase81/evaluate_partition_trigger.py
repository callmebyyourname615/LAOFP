#!/usr/bin/env python3
import json,sys
src,out=sys.argv[1:]; d=json.load(open(src))
peak=float(d.get('observedPeakTps',0)); synthetic=bool(d.get('synthetic',True))
trigger=peak>5000 and not synthetic
json.dump({'observedPeakTps':peak,'synthetic':synthetic,'triggered':trigger,
 'decision':'IMPLEMENT_HOURLY_PARTITIONS' if trigger else 'KEEP_DAILY_PARTITIONS',
 'migrationGenerated':False},open(out,'w'),indent=2,sort_keys=True)
