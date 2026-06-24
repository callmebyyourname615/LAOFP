#!/usr/bin/env python3
import json
required={'BUILD','FINANCIAL'}
def decide(results, approvals):
    if any(x not in results for x in required): return 'PREPARED'
    if any(results[x].get('synthetic') for x in required): return 'NO_GO'
    if any(results[x]['status']!='PASS' for x in required): return 'NO_GO'
    if not approvals: return 'BLOCKED'
    return 'GO'
assert decide({},False)=='PREPARED'
assert decide({'BUILD':{'status':'PASS','synthetic':False},'FINANCIAL':{'status':'PASS','synthetic':True}},True)=='NO_GO'
assert decide({'BUILD':{'status':'PASS','synthetic':False},'FINANCIAL':{'status':'FAIL','synthetic':False}},True)=='NO_GO'
assert decide({'BUILD':{'status':'PASS','synthetic':False},'FINANCIAL':{'status':'PASS','synthetic':False}},False)=='BLOCKED'
assert decide({'BUILD':{'status':'PASS','synthetic':False},'FINANCIAL':{'status':'PASS','synthetic':False}},True)=='GO'
print(json.dumps({'status':'PASS','cases':5}))
