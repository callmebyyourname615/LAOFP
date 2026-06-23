#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path

PUBLIC_AUTH = {'/api/auth/login', '/api/auth/mfa', '/api/auth/refresh', '/api/auth/logout'}
MAPPING = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?')
REQUEST = re.compile(r'@RequestMapping\("([^"]+)"\)')
AUTH = re.compile(r'@(PreAuthorize|Secured|RolesAllowed)\((.+)\)')


def main() -> int:
    p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,default=Path('.')); p.add_argument('--output',type=Path,required=True); p.add_argument('--matrix',type=Path,required=True); a=p.parse_args()
    root=a.root.resolve(); controller_dir=root/'src/main/java/com/example/switching/usermgmt/controller'; security=(root/'src/main/java/com/example/switching/security/config/SecurityConfig.java').read_text(encoding='utf-8')
    endpoints=[]; errors=[]
    for path in sorted(controller_dir.glob('*.java')):
        source=path.read_text(encoding='utf-8'); base_match=REQUEST.search(source); base=base_match.group(1) if base_match else ''
        class_auth=''
        class_prefix=source.split('public class',1)[0]
        found=AUTH.findall(class_prefix)
        if found: class_auth=found[-1][1]
        pending_auth=''
        pending_mapping=None
        pending_line=0
        for idx,line in enumerate(source.splitlines(),1):
            auth=AUTH.search(line)
            if auth: pending_auth=auth.group(2)
            mapping=MAPPING.search(line)
            if mapping:
                pending_mapping=(mapping.group(1).upper(), mapping.group(2) or '')
                pending_line=idx
            if pending_mapping and re.search(r'\bpublic\s+(?:ResponseEntity|List|void|[A-Z][A-Za-z0-9_<>?, ]+)\s*<*', line):
                method,suffix=pending_mapping
                endpoint=base+suffix
                effective=pending_auth or class_auth
                public=endpoint in PUBLIC_AUTH
                endpoints.append({'method':method,'path':endpoint,'authorization':effective or ('permitAll' if public else ''),'source':f'{path.relative_to(root)}:{pending_line}','public':public})
                if not public and not effective:
                    errors.append(f'{method} {endpoint} lacks visible method/class authorization')
                pending_auth=''
                pending_mapping=None
                pending_line=0
    for endpoint in PUBLIC_AUTH:
        if endpoint not in security or '.permitAll()' not in security:
            errors.append(f'public auth endpoint/security allowlist not found: {endpoint}')
    maker=(root/'src/main/java/com/example/switching/usermgmt/service/MakerCheckerService.java').read_text(encoding='utf-8')
    if not re.search(r'(?i)(maker|requester).*(checker|approver)|checker.*maker|approver.*requester', maker, re.S):
        errors.append('maker-checker service does not expose an obvious same-user separation check')
    migrations='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in sorted((root/'src/main/resources/db/migration').glob('V*__*.sql')) if 'smos' in p.name.lower() or 'user' in p.name.lower())
    for role in ('SYSTEM_ADMIN','OPS_ADMIN','SETTLEMENT_OFFICER','DISPUTE_OFFICER','RISK_OFFICER','AUDITOR','PARTICIPANT_ADMIN','READ_ONLY'):
        if role not in migrations: errors.append(f'expected SMOS role not found in migrations: {role}')
    doc={'schemaVersion':1,'passed':not errors,'endpointCount':len(endpoints),'publicEndpoints':sorted(PUBLIC_AUTH),'endpoints':endpoints,'errors':errors}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    lines=['# SMOS RBAC Permission Matrix','', '| Method | Path | Authorization | Public | Source |','|---|---|---|---|---|']
    for item in endpoints: lines.append(f"| {item['method']} | `{item['path']}` | `{item['authorization']}` | {'yes' if item['public'] else 'no'} | `{item['source']}` |")
    lines += ['', f"Result: **{'PASS' if not errors else 'FAIL'}**", '']
    a.matrix.write_text('\n'.join(lines),encoding='utf-8')
    print(f"Phase 63H SMOS RBAC audit: {'PASS' if not errors else 'FAIL'} ({len(endpoints)} endpoints)")
    for error in errors: print('  ERROR:',error)
    return 0 if not errors else 1


if __name__=='__main__': raise SystemExit(main())
