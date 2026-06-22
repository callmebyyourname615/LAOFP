#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, fnmatch, hashlib, json, math, pathlib, statistics, sys
from typing import Any
import yaml
UTC=dt.timezone.utc

def load(path: str|pathlib.Path)->Any:
    p=pathlib.Path(path)
    with p.open(encoding='utf-8') as f:
        return json.load(f) if p.suffix.lower()=='.json' else yaml.safe_load(f)

def write(path: str|pathlib.Path,data:Any)->None:
    p=pathlib.Path(path); p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,indent=2,sort_keys=True)+'\n',encoding='utf-8')

def parse_time(value:str)->dt.datetime:
    if value.endswith('Z'): value=value[:-1]+'+00:00'
    out=dt.datetime.fromisoformat(value)
    if out.tzinfo is None: raise ValueError('timestamp must include timezone')
    return out.astimezone(UTC)

def now_utc(value:str|None=None)->dt.datetime:
    return parse_time(value) if value else dt.datetime.now(UTC)

def age_seconds(captured:str, now:dt.datetime)->float:
    age=(now-parse_time(captured)).total_seconds()
    if age<0: raise ValueError('capturedAt is in the future')
    return age

def sha256(path:str|pathlib.Path)->str:
    h=hashlib.sha256()
    with pathlib.Path(path).open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

def nested(data:dict,path:str,default=None):
    cur:Any=data
    for part in path.split('.'):
        if not isinstance(cur,dict) or part not in cur: return default
        cur=cur[part]
    return cur

def compare(value:float,op:str,threshold:float)->bool:
    return {'eq':value==threshold,'lte':value<=threshold,'lt':value<threshold,'gte':value>=threshold,'gt':value>threshold}[op]

def ensure_fresh(data:dict,max_age:int,now:dt.datetime)->float:
    if 'capturedAt' not in data: raise ValueError('capturedAt is required')
    age=age_seconds(data['capturedAt'],now)
    if age>max_age: raise ValueError(f'snapshot is stale: ageSeconds={int(age)} max={max_age}')
    return age

def release_identity(data:dict)->tuple[str,str,str]:
    r=data.get('release',{})
    return r.get('reference',''),r.get('gitCommit',''),r.get('imageDigest','')

def robust_z(value:float,samples:list[float])->float:
    if len(samples)<2: return 0.0
    median=statistics.median(samples); deviations=[abs(x-median) for x in samples]; mad=statistics.median(deviations)
    if mad==0: return 0.0 if value==median else math.inf
    return 0.6745*(value-median)/mad

def safe_relpath(value:str)->bool:
    p=pathlib.PurePosixPath(value)
    return not p.is_absolute() and '..' not in p.parts and '\\' not in value and '\x00' not in value
