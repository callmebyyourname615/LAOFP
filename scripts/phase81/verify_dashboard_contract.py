#!/usr/bin/env python3
import json,pathlib,re
root=pathlib.Path('.')
controllers=list((root/'src/main/java/com/example/switching/dashboard').rglob('*DashboardController.java'))
required={'/api/dashboard/transactions','/api/dashboard/participants','/api/dashboard/infrastructure','/api/dashboard/dr'}
found=set(); errors=[]
for p in controllers:
    s=p.read_text()
    m=re.search(r'@RequestMapping\("([^"]+)"\)',s)
    if m: found.add(m.group(1))
    if m and m.group(1) in required:
        if 'CacheControl.noStore()' not in s: errors.append(f'{p}: no-store missing')
        if '@PreAuthorize' not in s: errors.append(f'{p}: RBAC missing')
errors += [f'missing endpoint {x}' for x in sorted(required-found)]
print(json.dumps({'passed':not errors,'endpoints':sorted(found & required),'errors':errors},indent=2,sort_keys=True))
raise SystemExit(0 if not errors else 1)
