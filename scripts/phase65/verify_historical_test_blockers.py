#!/usr/bin/env python3
from pathlib import Path
import re, sys
errors=[]

def read(path):
    p=Path(path)
    if not p.is_file(): errors.append(f'missing {path}'); return ''
    return p.read_text(encoding='utf-8')

migration=read('src/main/java/com/example/switching/migration/MigrationApplication.java')
if 'ObjectMapper migrationObjectMapper()' not in migration or '@ConditionalOnMissingBean(ObjectMapper.class)' not in migration:
    errors.append('MigrationApplication must provide an ObjectMapper for isolated webhook crypto wiring')
webhook=read('src/main/java/com/example/switching/webhook/crypto/WebhookEncryptionConfiguration.java')
if 'ObjectMapper objectMapper' not in webhook:
    errors.append('WebhookEncryptionConfiguration does not receive ObjectMapper explicitly')
sanctions=read('src/main/java/com/example/switching/aml/service/SanctionsListSyncService.java')
for token in ('provider_uid','Timestamp.from(Instant.now())'):
    if token not in sanctions: errors.append(f'Sanctions seed/sync regression: missing {token}')
ops=read('src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java')
log_pos=ops.find('DELETE FROM psp_suspension_log'); participant_pos=ops.find('DELETE FROM participants')
if log_pos < 0 or participant_pos < 0 or log_pos > participant_pos:
    errors.append('Operations cleanup must delete psp_suspension_log before participants')
for p in list(Path('src/main/java').rglob('*.java'))+list(Path('src/test/java').rglob('*.java')):
    text=p.read_text(encoding='utf-8',errors='ignore')
    if re.search(r'setObject\s*\([^,]+,\s*(?:Instant\.now\(\)|\w*[Ii]nstant\w*)\s*\)', text):
        errors.append(f'{p}: untyped JDBC Instant setObject binding')
if errors:
    print('\n'.join('FAIL: '+x for x in errors)); sys.exit(1)
print('PASS: historical ObjectMapper, sanctions provider_uid, FK cleanup and Instant-binding blockers are closed')
