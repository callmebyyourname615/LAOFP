#!/usr/bin/env python3
import json, sys

def read_pair(path):
    raw=open(path, encoding='utf-8').read().strip().replace('|', ',').split(',')
    return int(raw[0]), float(raw[1])
left=read_pair(sys.argv[1]); right=read_pair(sys.argv[2])
print(json.dumps({"status":"MATCHED" if left==right else "EXCEPTION", "left":{"count":left[0],"amount":left[1]}, "right":{"count":right[0],"amount":right[1]}, "mismatchCount": abs(left[0]-right[0])}, sort_keys=True))
