#!/usr/bin/env python3
from pathlib import Path
import json, sys, yaml
fail=[]
rule=Path('monitoring/prometheus/phase62-database-pool-rules.yaml')
try: data=yaml.safe_load(rule.read_text())
except Exception as e: fail.append(f'invalid Hikari rules: {e}'); data={}
text=rule.read_text() if rule.exists() else ''
for m in ('SwitchingHikariPoolUtilizationHigh','SwitchingHikariPoolThreadsPending','SwitchingHikariAcquireLatencyHigh','SwitchingReadReplicaPoolUnavailable'):
 if m not in text: fail.append(f'missing alert {m}')
try: json.loads(Path('monitoring/grafana/dashboards/switching-database-pools.json').read_text())
except Exception as e: fail.append(f'invalid Grafana JSON: {e}')
for p in ('src/main/resources/application.yml','src/main/resources/application-prod.yml'):
 t=Path(p).read_text()
 if 'hikaricp: true' not in t: fail.append(f'{p} does not enable Hikari metrics')
if not Path('docs/runbooks/HIKARI_POOL_SATURATION.md').is_file(): fail.append('runbook missing')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: Hikari metrics, alerts, dashboard and runbook')
