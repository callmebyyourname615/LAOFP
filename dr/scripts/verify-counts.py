#!/usr/bin/env python3
import csv,json,sys

def read(path):
    with open(path) as f: return {row[0]:int(row[1]) for row in csv.reader(f)}
before,after=read(sys.argv[1]),read(sys.argv[2]); violations=[]
for key,value in before.items():
    if after.get(key,-1) < value: violations.append({'table':key,'before':value,'after':after.get(key)})
result={'before':before,'after':after,'violations':violations,'passed':not violations}
print(json.dumps(result,indent=2))
if violations: sys.exit(1)
