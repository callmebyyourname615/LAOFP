#!/usr/bin/env python3
import decimal, pathlib, re, sys
import yaml

def positive(value, name):
    try: number=decimal.Decimal(str(value))
    except decimal.InvalidOperation as exc: raise SystemExit(f'{name} must be numeric') from exc
    if number <= 0: raise SystemExit(f'{name} must be positive')

def main(path):
    doc=yaml.safe_load(pathlib.Path(path).read_text(encoding='utf-8'))
    if doc.get('version') != 1: raise SystemExit('version must be 1')
    entitlements=doc.get('entitlements') or []
    limits=doc.get('limits') or []
    if not entitlements or not limits: raise SystemExit('entitlements and limits are required')
    entitlement_keys=set()
    for i,item in enumerate(entitlements):
        for key in ('participant','product','channel','currency','effective_from'):
            if not item.get(key): raise SystemExit(f'entitlements[{i}].{key} is required')
        if not re.fullmatch(r'[A-Z]{3}',str(item['currency'])): raise SystemExit('currency must be ISO-4217 uppercase')
        key=(item['participant'],item['product'],item['channel'],item['currency'])
        if key in entitlement_keys: raise SystemExit(f'duplicate entitlement {key}')
        entitlement_keys.add(key)
    active_keys=set()
    for i,item in enumerate(limits):
        for key in ('name','scope_type','scope_value','product','channel','currency','per_transaction_amount','timezone'):
            if item.get(key) in (None,''): raise SystemExit(f'limits[{i}].{key} is required')
        if item['scope_type'] not in {'SYSTEM','PARTICIPANT','PRODUCT','PARTICIPANT_PRODUCT'}: raise SystemExit('invalid scope_type')
        for key in ('per_transaction_amount','hourly_amount','daily_amount'):
            if item.get(key) is not None: positive(item[key],f'limits[{i}].{key}')
        if item.get('daily_count') is not None and (not isinstance(item['daily_count'],int) or item['daily_count']<=0): raise SystemExit('daily_count must be a positive integer')
        key=(item['scope_type'],item['scope_value'],item['product'],item['channel'],item['currency'])
        if key in active_keys: raise SystemExit(f'duplicate active limit scope {key}')
        active_keys.add(key)
    print(f'validated {len(entitlements)} entitlements and {len(limits)} limit policies')
if __name__=='__main__':
    if len(sys.argv)!=2: raise SystemExit('usage: validate_limit_policy.py POLICY.yaml')
    main(sys.argv[1])
