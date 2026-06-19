#!/usr/bin/env python3
import decimal, hashlib, json, pathlib, sys
p=pathlib.Path(sys.argv[1])
data=json.loads(p.read_text(encoding='utf-8'))
required={'planCode','version','validFrom','rules'}
missing=required-set(data)
if missing: raise SystemExit('missing fields: '+','.join(sorted(missing)))
if not isinstance(data['version'],int) or data['version']<1: raise SystemExit('version must be positive integer')
if not data['rules']: raise SystemExit('at least one tariff rule is required')
for i,r in enumerate(data['rules']):
    for key in ('messageType','currency','flatFee','rateBasisPoints'):
        if key not in r: raise SystemExit(f'rule {i} missing {key}')
    if len(r['currency'])!=3 or r['currency']!=r['currency'].upper(): raise SystemExit(f'rule {i} invalid currency')
    if decimal.Decimal(str(r['flatFee']))<0 or decimal.Decimal(str(r['rateBasisPoints']))<0: raise SystemExit(f'rule {i} negative fee')
canonical=json.dumps(data,sort_keys=True,separators=(',',':')).encode()
print(hashlib.sha256(canonical).hexdigest())
