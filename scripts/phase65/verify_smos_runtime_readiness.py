#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path
PLACE=re.compile(r'(?i)(replace|todo|tbd|change_me|example\.invalid)')

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--attestation',type=Path)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--static-only',action='store_true')
    a=p.parse_args(); errors=[]
    required=['docs/security/SMOS_PERMISSION_MATRIX.md','docs/openapi/smos-api.yaml','src/main/java/com/example/switching/usermgmt/config/SmosBootstrapAdminRunner.java']
    for x in required:
        if not Path(x).is_file(): errors.append('missing '+x)
    boot=Path(required[-1]).read_text() if Path(required[-1]).is_file() else ''
    for token in ('acknowledge-one-time','users.count() > 0','passwordPolicy.validate','mfa-secret'):
        if token not in boot: errors.append('bootstrap safeguard missing: '+token)
    for path in Path('src/main/java/com/example/switching/usermgmt/controller').glob('*Controller.java'):
        text=path.read_text(); class_guard='@PreAuthorize' in text.split('public class',1)[0]
        if '/api/admin' in text and not class_guard:
            for m in re.finditer(r'@(PostMapping|PutMapping|PatchMapping|DeleteMapping)',text):
                if '@PreAuthorize' not in text[m.start():m.start()+400]: errors.append(f'{path}: unguarded write mapping')
    att={}
    if not a.static_only:
        if not a.attestation or not a.attestation.is_file(): errors.append('SMOS attestation missing')
        else:
            att=json.loads(a.attestation.read_text())
            for k in ('mfaMandatory','allUsersEnrolledTotp','bootstrapDisabledAfterProvisioning','adminEndpointAuditPassed','crossParticipantIsolationPassed','makerCheckerPassed','openApiValidated'):
                if att.get(k) is not True: errors.append(k+' must be true')
            if int(att.get('initialUsersCreated',0))<5: errors.append('at least five initial operator users required')
            for k in ('qaLead','securityLead','signedAt'):
                if not isinstance(att.get(k),str) or not att[k].strip() or PLACE.search(att[k]): errors.append(k+' missing/placeholder')
    out={'schemaVersion':1,'passed':not errors,'staticOnly':a.static_only,'attestation':att,'errors':errors}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,indent=2)+'\n')
    print('PASS' if not errors else '\n'.join('FAIL: '+x for x in errors))
    return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
