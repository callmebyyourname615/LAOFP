#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
for path,marks in {
 'src/main/resources/db/migration/V104__standardize_financial_numeric_precision.sql':['NUMERIC(24,4)','financial_precision_policy','FX_RATE'],
 'src/main/java/com/example/switching/financial/MoneyPrecisionPolicy.java':['PRECISION = 24','SCALE = 4','RoundingMode.HALF_EVEN'],
 'src/test/java/com/example/switching/financial/MoneyPrecisionPolicyTest.java':['rejectsOverflow','roundsWithHalfEvenAtFourDecimals'],
 'docs/architecture/FINANCIAL_PRECISION_POLICY.md':['`NUMERIC(24,4)`','`NUMERIC(24,10)`','double']}.items():
 p=Path(path)
 if not p.is_file(): fail.append(f'missing {path}'); continue
 t=p.read_text()
 for m in marks:
  if m not in t: fail.append(f'{path} missing {m!r}')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: financial precision migration and Java policy')
