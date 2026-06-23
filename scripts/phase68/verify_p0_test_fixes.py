#!/usr/bin/env python3
import argparse, json, re, sys
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,default=Path('.')); p.add_argument('--output',type=Path); a=p.parse_args(); root=a.root.resolve(); errors=[]; checks={}
def read(rel):
    f=root/rel
    if not f.is_file(): errors.append(f'missing {rel}'); return ''
    return f.read_text(errors='replace')
# Vault ObjectMapper wiring
migration=read('src/main/java/com/example/switching/migration/MigrationApplication.java')
checks['migrationObjectMapperBean']=all(x in migration for x in ['ObjectMapper migrationObjectMapper()', '@ConditionalOnMissingBean(ObjectMapper.class)', 'JsonMapper.builder()'])
if not checks['migrationObjectMapperBean']: errors.append('MigrationApplication does not provide a conditional ObjectMapper bean')
# Sanctions provider uid seed
sanctions=read('src/test/java/com/example/switching/aml/SanctionsScreeningIntegrationTest.java')
checks['sanctionsProviderUid']=('provider_uid' in sanctions and 'IS NOT NULL' in sanctions)
if not checks['sanctionsProviderUid']: errors.append('SanctionsScreeningIntegrationTest does not assert provider_uid')
# FK cleanup order
ops=read('src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java')
psp=ops.find('DELETE FROM psp_suspension_log'); participants=ops.find('DELETE FROM participants')
checks['fkCleanupOrder']=psp >= 0 and participants >= 0 and psp < participants
if not checks['fkCleanupOrder']: errors.append('psp_suspension_log must be deleted before participants')
# Typed temporal binding / no raw Instant setObject in cross-border test
cross=read('src/test/java/com/example/switching/crossborder/CrossBorderAmlBlockIntegrationTest.java')
raw=bool(re.search(r'setObject\s*\([^;\n]*Instant',cross))
typed=('Types.TIMESTAMP_WITH_TIMEZONE' in cross or 'Timestamp.from(' in cross or not raw)
checks['typedInstantBinding']=typed
if not typed: errors.append('cross-border test contains untyped Instant setObject binding')
# Explicit admin/settlement authorization
for rel, token in {
 'src/main/java/com/example/switching/settlement/controller/SettlementController.java':'PERM_SETTLEMENT_APPROVE',
 'src/main/java/com/example/switching/participant/controller/ParticipantController.java':'PERM_PARTICIPANT_MANAGE',
 'src/main/java/com/example/switching/participant/controller/ParticipantCredentialController.java':'PERM_PARTICIPANT_MANAGE',
}.items():
    text=read(rel); key='authorization:'+rel; checks[key]='@PreAuthorize' in text and token in text
    if not checks[key]: errors.append(f'missing explicit authorization in {rel}')
result={'schemaVersion':1,'passed':not errors,'checks':checks,'errors':errors}
if a.output: a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps(result,indent=2,sort_keys=True)); sys.exit(0 if not errors else 1)
