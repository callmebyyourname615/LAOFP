#!/usr/bin/env python3
import csv, sys
allowed={"ALLOW","REVIEW","REJECT","HOLD"}
with open(sys.argv[1], newline='', encoding='utf-8') as f:
    for row in csv.DictReader(f):
        if row.get('action') not in allowed: raise SystemExit(f"invalid action {row}")
        if int(row.get('window_seconds','0')) <= 0: raise SystemExit(f"invalid window {row}")
print('velocity rules valid')
