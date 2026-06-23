#!/usr/bin/env python3
from __future__ import annotations
import argparse,json
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--path',required=True); p.add_argument('--output',required=True); a=p.parse_args()
try: d=json.loads(Path(a.path).read_text())
except Exception as exc: print(exc); raise SystemExit(1)
required=['application_instance_failure','postgres_primary_failure','kafka_broker_failure','network_partition','object_storage_outage','external_api_timeout','replica_promotion_failback','outbox_replay','duplicate_transaction_protection']
passed=(d.get('synthetic') is False and float(d.get('rpoMinutes',999)) < 5 and float(d.get('rtoMinutes',999)) < 30 and int(d.get('transactionLoss',1))==0 and int(d.get('financialMismatch',1))==0 and all(d.get('scenarios',{}).get(x) is True for x in required) and d.get('backupVerified') is True and d.get('isolatedRestoreVerified') is True and d.get('pitrVerified') is True)
out={'schemaVersion':1,'passed':passed,**d}; Path(a.output).write_text(json.dumps(out,indent=2,sort_keys=True)+'\n')
print('PASS' if passed else 'FAIL'); raise SystemExit(0 if passed else 1)
