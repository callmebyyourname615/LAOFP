#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
def req(path,*marks):
 p=Path(path)
 if not p.is_file(): fail.append(f'missing {path}'); return
 t=p.read_text()
 for m in marks:
  if m not in t: fail.append(f'{path} missing {m!r}')
req('src/main/java/com/example/switching/config/TransactionRoutingDataSource.java','TransactionSynchronizationManager.isCurrentTransactionReadOnly','ReadReplicaRoutingContext.currentOverride')
req('src/main/java/com/example/switching/config/ArchiveDataSourceConfig.java','LazyConnectionDataSourceProxy','SwitchingReadReplicaPool','setLenientFallback(false)')
req('src/main/java/com/example/switching/config/ReadReplicaProperties.java','maximumPoolSize = 20','validate()')
req('src/test/java/com/example/switching/config/TransactionRoutingDataSourceTest.java','routesReadOnlyTransactionToReplica','explicitPrimaryOverrideWins')
req('docs/architecture/READ_REPLICA_ROUTING.md','Read Replica Routing','pg_is_in_recovery','fail fast')
req('scripts/phase62/certify_read_replica_uat.sh','PRIMARY_DB_PSQL_DSN','READ_REPLICA_PSQL_DSN','pg_is_in_recovery')
for path in Path('src/main/java/com/example/switching/dashboard').glob('*/service/*DashboardService.java'):
 if '@Transactional(readOnly = true)' not in path.read_text(): fail.append(f'{path} not read-only')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: read-replica routing and primary consistency override contract')
