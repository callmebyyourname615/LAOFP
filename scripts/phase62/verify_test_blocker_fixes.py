#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]

def require(path,*markers):
    p=Path(path)
    if not p.is_file(): fail.append(f'missing {path}'); return
    text=p.read_text()
    for m in markers:
        if m not in text: fail.append(f'{path} missing {m!r}')

require('src/main/java/com/example/switching/migration/MigrationApplication.java',
        'ConditionalOnMissingBean(ObjectMapper.class)','JsonMapper.builder()')
require('src/main/java/com/example/switching/aml/service/SanctionsListSyncService.java',
        'provider_uid','Timestamp.from(Instant.now())')
require('src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java',
        'DELETE FROM psp_suspension_log','DELETE FROM participants')
ops=Path('src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java').read_text()
if ops.index('DELETE FROM psp_suspension_log') > ops.index('DELETE FROM participants'):
    fail.append('operations cleanup deletes participants before suspension log')
cb=Path('src/test/java/com/example/switching/crossborder/CrossBorderAmlBlockIntegrationTest.java').read_text()
if 'setObject(' in cb and 'TIMESTAMP_WITH_TIMEZONE' not in cb:
    fail.append('cross-border test still binds Instant without explicit SQL type')
if fail:
    print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: historic test blockers have regression guards')
