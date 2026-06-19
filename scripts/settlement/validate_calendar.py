#!/usr/bin/env python3
import datetime as dt, pathlib, sys, zoneinfo
import yaml

def parse_time(value,name):
    try: return dt.time.fromisoformat(str(value))
    except ValueError as exc: raise SystemExit(f'{name} is not ISO time') from exc

def main(path):
    doc=yaml.safe_load(pathlib.Path(path).read_text(encoding='utf-8'))
    for key in ('calendar_code','version','timezone','weekend_days','effective_from','cutoffs'):
        if doc.get(key) in (None,'',[]): raise SystemExit(f'{key} is required')
    try: zoneinfo.ZoneInfo(doc['timezone'])
    except Exception as exc: raise SystemExit('unknown IANA timezone') from exc
    weekends=doc['weekend_days']
    if len(set(weekends))!=len(weekends) or any(not isinstance(v,int) or v<1 or v>7 for v in weekends): raise SystemExit('weekend_days must contain unique ISO weekdays 1..7')
    holiday_dates=set()
    for i,h in enumerate(doc.get('holidays',[])):
        date=dt.date.fromisoformat(str(h.get('date')))
        if date in holiday_dates: raise SystemExit(f'duplicate holiday {date}')
        holiday_dates.add(date)
        if not h.get('name'): raise SystemExit(f'holidays[{i}].name is required')
        if not h.get('full_day',True) and not h.get('early_close_time'): raise SystemExit('partial holiday requires early_close_time')
    cutoff_keys=set()
    for i,c in enumerate(doc['cutoffs']):
        for key in ('cycle_code','product_code','submission_cutoff','finality_cutoff','late_action'):
            if not c.get(key): raise SystemExit(f'cutoffs[{i}].{key} is required')
        submission=parse_time(c['submission_cutoff'],'submission_cutoff'); final=parse_time(c['finality_cutoff'],'finality_cutoff')
        if final<submission: raise SystemExit('finality_cutoff must not be before submission_cutoff')
        if c['late_action'] not in {'REJECT','NEXT_CYCLE','MANUAL_REVIEW'}: raise SystemExit('invalid late_action')
        grace=c.get('grace_seconds',0)
        if not isinstance(grace,int) or not 0<=grace<=3600: raise SystemExit('grace_seconds must be 0..3600')
        key=(c['cycle_code'],c['product_code'])
        if key in cutoff_keys: raise SystemExit(f'duplicate cutoff {key}')
        cutoff_keys.add(key)
    print(f'validated calendar {doc["calendar_code"]} with {len(holiday_dates)} holidays and {len(cutoff_keys)} cutoffs')
if __name__=='__main__':
    if len(sys.argv)!=2: raise SystemExit('usage: validate_calendar.py CALENDAR.yaml')
    main(sys.argv[1])
