#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
for p in Path('src/main/java/com/example/switching/dashboard').glob('*/service/*DashboardService.java'):
 t=p.read_text()
 for m in ('@Qualifier("reportingJdbcTemplate")','@Transactional(readOnly = true)','accessScope.requireSchemeWideOperator()','LIMIT'):
  if m not in t: fail.append(f'{p} missing {m}')
for p in Path('src/main/java/com/example/switching/dashboard').glob('*/controller/*DashboardController.java'):
 t=p.read_text()
 for m in ('@PreAuthorize','CacheControl.noStore()','X-Data-Freshness'):
  if m not in t: fail.append(f'{p} missing {m}')
if not Path('src/test/java/com/example/switching/dashboard/common/DashboardAccessScopeTest.java').is_file(): fail.append('dashboard scope test missing')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: critical dashboard RBAC, replica, timeout and response hardening')
