#!/usr/bin/env python3
import json,sys,subprocess
source,kind,out=sys.argv[1:]
data=json.load(open(source))
commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
assert data.get('type')==kind, 'attestation type mismatch'
assert data.get('approved') is True, 'not approved'
assert data.get('synthetic') is False, 'synthetic evidence rejected'
assert data.get('gitCommit')==commit, 'git commit mismatch'
assert data.get('signers'), 'signers missing'
if kind in {'backup','dr'}:
    assert data.get('financialMismatch',0)==0
    assert data.get('transactionLoss',0)==0
json.dump(data,open(out,'w'),indent=2,sort_keys=True)
