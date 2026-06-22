#!/usr/bin/env python3
import json, hashlib
from datetime import datetime, timezone
from pathlib import Path

def load(path): return json.loads(Path(path).read_text(encoding='utf-8'))
def dump(path,obj):
 p=Path(path); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n',encoding='utf-8')
def dt(s): return datetime.fromisoformat(s.replace('Z','+00:00'))
def age_minutes(captured, now): return (dt(now)-dt(captured)).total_seconds()/60
def sha_ok(v): return isinstance(v,str) and len(v)==64 and all(c in '0123456789abcdef' for c in v)
def identity(d):
 r=d.get('release',d)
 return r.get('reference') or r.get('releaseReference'), r.get('gitCommit'), r.get('imageDigest')
def assert_identity(d,ref,commit,digest):
 if identity(d)!=(ref,commit,digest): raise ValueError('release identity mismatch')
def result(status,checks,**extra): return {'schemaVersion':1,'status':status,'checks':checks,**extra}
def pass_status(checks): return 'PASS' if checks and all(x.get('status')=='PASS' for x in checks) else 'FAIL'
