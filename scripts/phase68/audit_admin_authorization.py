#!/usr/bin/env python3
import argparse, json, re, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,default=Path('.')); p.add_argument('--output',type=Path); a=p.parse_args(); root=a.root.resolve(); errors=[]; audited=[]
requirements={
 'src/main/java/com/example/switching/usermgmt/controller/UserController.java':['@PreAuthorize','SYSTEM_ADMIN'],
 'src/main/java/com/example/switching/usermgmt/controller/MakerCheckerController.java':['PERM_MAKER_CHECKER_SUBMIT','PERM_MAKER_CHECKER_APPROVE'],
 'src/main/java/com/example/switching/settlement/controller/SettlementController.java':['PERM_SETTLEMENT_VIEW','PERM_SETTLEMENT_APPROVE'],
 'src/main/java/com/example/switching/participant/controller/ParticipantController.java':['PERM_PARTICIPANT_VIEW','PERM_PARTICIPANT_MANAGE'],
 'src/main/java/com/example/switching/participant/controller/ParticipantCredentialController.java':['PERM_PARTICIPANT_MANAGE'],
}
for rel,tokens in requirements.items():
 f=root/rel
 if not f.is_file(): errors.append(f'missing {rel}'); continue
 text=f.read_text(errors='replace'); missing=[t for t in tokens if t not in text]
 audited.append({'path':rel,'requiredTokens':tokens,'missingTokens':missing})
 if missing: errors.append(f'{rel}: missing {missing}')
# Ensure every state-changing mapping in selected controller families has method or class authorization.
for rel in [x for x in requirements if '/controller/' in x]:
 text=(root/rel).read_text(errors='replace')
 class_auth='@PreAuthorize' in text[:text.find('public class')]
 lines=text.splitlines()
 for i,line in enumerate(lines):
  if re.search(r'@(Post|Put|Patch|Delete)Mapping',line):
   window='\n'.join(lines[max(0,i-3):i+2])
   if not class_auth and '@PreAuthorize' not in window: errors.append(f'{rel}:{i+1}: write endpoint has no explicit @PreAuthorize')
result={'schemaVersion':1,'passed':not errors,'audited':audited,'errors':errors}
if a.output: a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps(result,indent=2,sort_keys=True)); sys.exit(0 if not errors else 1)
