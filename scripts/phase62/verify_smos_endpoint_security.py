#!/usr/bin/env python3
from pathlib import Path
import re, sys, yaml

failures=[]
controllers=Path('src/main/java/com/example/switching/usermgmt/controller')
for path in controllers.glob('*Controller.java'):
    text=path.read_text()
    if '/api/admin/' not in text:
        continue
    class_guard='@PreAuthorize' in text.split('public class',1)[0]
    # Every state-changing or list method in admin controllers must have class or method guard.
    for match in re.finditer(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)[\s\S]{0,260}?public\s+ResponseEntity', text):
        snippet=match.group(0)
        if not class_guard and '@PreAuthorize' not in snippet:
            failures.append(f'{path}: {match.group(1)} lacks @PreAuthorize')

for path in Path('src/main/java/com/example/switching/dashboard').glob('*/controller/*Controller.java'):
    text=path.read_text()
    if '@PreAuthorize' not in text:
        failures.append(f'{path}: dashboard endpoint lacks @PreAuthorize')
    if 'CacheControl.noStore()' not in text or 'X-Data-Freshness' not in text:
        failures.append(f'{path}: dashboard response lacks no-store/freshness controls')

spec=Path('docs/openapi/smos-api.yaml')
if not spec.exists(): failures.append('SMOS OpenAPI spec missing')
else:
    data=yaml.safe_load(spec.read_text())
    required={'/auth/login','/auth/mfa','/auth/refresh','/auth/logout','/admin/users','/admin/requests'}
    missing=required-set(data.get('paths',{}))
    if missing: failures.append(f'OpenAPI missing paths: {sorted(missing)}')

matrix=Path('docs/security/SMOS_PERMISSION_MATRIX.md')
if not matrix.exists() or 'PARTICIPANT_ADMIN' not in matrix.read_text():
    failures.append('SMOS permission matrix missing/incomplete')

if failures:
    print('\n'.join('FAIL: '+x for x in failures)); sys.exit(1)
print('PASS: SMOS endpoint security, permission matrix and OpenAPI contract')
