#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]; failures=[]
def req(rel,*markers):
 p=ROOT/rel
 if not p.is_file(): failures.append(f'missing {rel}'); return
 text=p.read_text(encoding='utf-8')
 for m in markers:
  if m not in text: failures.append(f'{rel}: missing {m!r}')
req('src/main/java/com/example/switching/dashboard/common/DashboardQueryGuard.java','statement_timeout','30000')
for domain in ('settlement','risk','crossborder'):
 cap=domain.capitalize() if domain!='crossborder' else 'CrossBorder'
 rel=f'src/main/java/com/example/switching/dashboard/{domain}/service/{cap}DashboardService.java'
 req(rel,'@Transactional(readOnly = true)','queryGuard.apply()','LIMIT')
req('src/main/java/com/example/switching/promotion/service/PromotionEligibilityEvaluator.java','MAX_CONDITIONS','MAX_IN_VALUES','FIELDS','OPERATORS')
app=(ROOT/'src/main/resources/application.yml').read_text(encoding='utf-8')
if 'enabled: ${PHASE_II_PROMOTION_ENABLED:false}' not in app: failures.append('promotion must be disabled by default')
if 'SMOS_DASHBOARD_QUERY_TIMEOUT_MS:3000' not in app: failures.append('dashboard query timeout config missing')
if failures:
 print(f'Phase 61E dashboard/promotion readiness: FAIL ({len(failures)} issues)'); [print('  ERROR:',x) for x in failures]; sys.exit(1)
print('Phase 61E dashboard/promotion readiness: PASS')
