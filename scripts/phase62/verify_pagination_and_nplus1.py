#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
checks={
 'src/main/java/com/example/switching/observability/sql/NPlusOneStatementInspector.java':['StatementInspector','fingerprint','ThreadLocal','queryWarningThreshold'],
 'src/main/java/com/example/switching/observability/sql/NPlusOneObservationFilter.java':['query_budget','repeated_statement'],
 'src/main/java/com/example/switching/usermgmt/controller/UserController.java':['@RequestParam(defaultValue = "0")','@RequestParam(defaultValue = "50")','Page<UserResponse>'],
 'src/main/java/com/example/switching/usermgmt/service/UserManagementService.java':['Math.min','100','PageRequest.of'],
 'src/main/java/com/example/switching/usermgmt/repository/UserRepository.java':['Page<UserEntity> findAll(Pageable pageable)','@EntityGraph']}
for p,marks in checks.items():
 path=Path(p)
 if not path.is_file(): fail.append(f'missing {p}'); continue
 t=path.read_text()
 for m in marks:
  if m not in t: fail.append(f'{p} missing {m!r}')
if 'NPlusOneStatementInspector' not in Path('src/main/resources/application.yml').read_text(): fail.append('Hibernate statement inspector not configured')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: N+1 instrumentation and bounded user pagination')
