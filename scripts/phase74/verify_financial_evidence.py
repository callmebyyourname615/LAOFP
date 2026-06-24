#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,pathlib
REQ={
 'settlement':['inputTransactions','eligibleTransactions','rejectedTransactions','deferredTransactions','settlementLegs','duplicateLegs','duplicatePostings','missingTransactions','negativeCounters','undeliveredOutbox','promotionOverspend','debitTotal','creditTotal','reconciliationDifference','durationSeconds','slaSeconds'],
 'performance':['smokePassed','sustained2kPassed','sustained10kPassed','burst20kPassed','soak8hPassed','maxErrorRate','maxP95Milliseconds','memoryLeakDetected','connectionExhaustionDetected','unboundedKafkaLagDetected']}
def main()->int:
 ap=argparse.ArgumentParser(); ap.add_argument('--kind',required=True,choices=REQ); ap.add_argument('--file',required=True); ap.add_argument('--output',required=True); a=ap.parse_args(); d=json.loads(pathlib.Path(a.file).read_text()); errors=[]
 for k in REQ[a.kind]:
  if k not in d: errors.append('missing '+k)
 if a.kind=='settlement' and not errors:
  if d['eligibleTransactions']+d['rejectedTransactions']+d['deferredTransactions']!=d['inputTransactions']: errors.append('population equation mismatch')
  for k in ['duplicateLegs','duplicatePostings','missingTransactions','negativeCounters','undeliveredOutbox','promotionOverspend']:
   if d[k]!=0: errors.append(f'{k} must be zero')
  if float(d['debitTotal'])!=float(d['creditTotal']): errors.append('debit/credit mismatch')
  if float(d['reconciliationDifference'])!=0: errors.append('reconciliation difference non-zero')
  if float(d['durationSeconds'])>float(d['slaSeconds']): errors.append('settlement SLA exceeded')
 if a.kind=='performance' and not errors:
  for k in ['smokePassed','sustained2kPassed','sustained10kPassed','burst20kPassed','soak8hPassed']:
   if d[k] is not True: errors.append(k+' is not true')
  for k in ['memoryLeakDetected','connectionExhaustionDetected','unboundedKafkaLagDetected']:
   if d[k] is not False: errors.append(k+' must be false')
 out={'schemaVersion':1,'kind':a.kind,'passed':not errors,'errors':errors}; pathlib.Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n'); print(json.dumps(out)); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
