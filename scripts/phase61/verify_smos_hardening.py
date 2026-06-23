#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]; failures=[]
def require(rel,*markers):
 p=ROOT/rel
 if not p.is_file(): failures.append(f'missing {rel}'); return ''
 text=p.read_text(encoding='utf-8')
 for marker in markers:
  if marker not in text: failures.append(f'{rel}: missing {marker!r}')
 return text
require('src/main/resources/db/migration/V101__smos_security_hardening.sql','participant_id','session_family_id','rotated_from_id','reset_mfa','reset_password')
require('src/main/java/com/example/switching/usermgmt/service/PasswordPolicyService.java','upper','lower','digit','symbol','prohibited weak pattern')
require('src/main/java/com/example/switching/usermgmt/service/AuthenticationService.java','SMOS_REFRESH_REUSE_DETECTED','revokeFamily','listSessions','revokeAllSessions','setLockedAt')
require('src/main/java/com/example/switching/usermgmt/service/SmosTokenService.java','switching-operator-api','Unsupported token header','Token lifetime exceeds policy','MAX_CLOCK_SKEW_SECONDS','participantId')
require('src/main/java/com/example/switching/usermgmt/controller/AuthController.java','/sessions','PERM_SESSION_VIEW','PERM_SESSION_REVOKE')
require('src/main/java/com/example/switching/usermgmt/service/UserManagementService.java','PARTICIPANT_ADMIN requires participantId scope','PasswordPolicyService','setParticipantId')
for rel in ('src/test/java/com/example/switching/usermgmt/service/PasswordPolicyServiceTest.java','src/test/java/com/example/switching/usermgmt/service/SmosSessionSecurityIntegrationTest.java','src/test/java/com/example/switching/migration/V101SmosSecurityHardeningMigrationIntegrationTest.java'):
 require(rel)
filter_text=(ROOT/'src/main/java/com/example/switching/usermgmt/filter/SmosJwtAuthenticationFilter.java').read_text(encoding='utf-8')
if 'roles.contains("PARTICIPANT_ADMIN")) authorities.add(new SimpleGrantedAuthority("ROLE_BANK"))' in filter_text:
 failures.append('PARTICIPANT_ADMIN must not inherit PSP ROLE_BANK payment-path authority')
if failures:
 print(f'Phase 61D SMOS hardening: FAIL ({len(failures)} issues)'); [print('  ERROR:',x) for x in failures]; sys.exit(1)
print('Phase 61D SMOS hardening: PASS')
