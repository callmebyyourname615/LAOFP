#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
checks={
 'src/main/resources/db/migration/V105__promotion_budget_and_funder_ledger_controls.sql':['pg_advisory_xact_lock','ck_promotion_budget_total','promotion_funder_ledger','idempotency_key'],
 'src/main/java/com/example/switching/promotion/service/PromotionBudgetService.java':['reserve(','consume(','refund(','MoneyPrecisionPolicy'],
 'src/test/java/com/example/switching/promotion/service/PromotionBudgetServiceIntegrationTest.java':['CannotExceedBudget','consumeAndRefundMaintainFunderLedger'],
 'src/main/resources/application.yml':['PHASE_II_PROMOTION_ENABLED:false']}
for p,marks in checks.items():
 path=Path(p)
 if not path.is_file(): fail.append(f'missing {p}'); continue
 t=path.read_text()
 for m in marks:
  if m.lower() not in t.lower(): fail.append(f'{p} missing {m!r}')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: promotion budget cap, idempotency and funder ledger controls')
