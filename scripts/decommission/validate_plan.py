#!/usr/bin/env python3
import datetime as dt,pathlib,sys
import yaml

def main(path):
    doc=yaml.safe_load(pathlib.Path(path).read_text(encoding='utf-8'))
    for key in ('plan_reference','target_type','target_code','planned_effective_at','reason','rollback_reference','approvals_required','tasks'):
        if doc.get(key) in (None,'',[]):raise SystemExit(f'{key} is required')
    if doc['target_type'] not in {'PARTICIPANT','CONNECTOR','PRODUCT','SERVICE','DATASET'}:raise SystemExit('invalid target_type')
    when=dt.datetime.fromisoformat(str(doc['planned_effective_at']))
    if when.tzinfo is None:raise SystemExit('planned_effective_at must include timezone')
    if set(doc['approvals_required']) != {'OPERATIONS','RISK','BUSINESS'}:raise SystemExit('approvals_required must be OPERATIONS,RISK,BUSINESS')
    codes=set();orders=0
    for i,task in enumerate(doc['tasks']):
        for key in ('code','owner_team','blocking'):
            if task.get(key) is None or task.get(key)=='':raise SystemExit(f'tasks[{i}].{key} is required')
        if task['code'] in codes:raise SystemExit(f'duplicate task code {task["code"]}')
        codes.add(task['code']);orders+=1
    if doc.get('data_exit_required') and not any(t['code']=='EXPORT_AND_ENCRYPT_DATA' and t['blocking'] for t in doc['tasks']):raise SystemExit('data exit requires blocking EXPORT_AND_ENCRYPT_DATA task')
    print(f'validated decommission plan with {orders} tasks')
if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('usage: validate_plan.py PLAN.yaml')
    main(sys.argv[1])
