#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
PLACEHOLDER = re.compile(r'(?i)(replace|todo|tbd|change_me|example)')


def text(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    p = argparse.ArgumentParser(); p.add_argument('--inventory', type=Path, required=True)
    p.add_argument('--delivery', type=Path, required=True); p.add_argument('--attestation', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True); a = p.parse_args()
    inventory = json.loads(a.inventory.read_text(encoding='utf-8'))
    delivery = json.loads(a.delivery.read_text(encoding='utf-8'))
    att = json.loads(a.attestation.read_text(encoding='utf-8')); errors=[]
    if inventory.get('passed') is not True: errors.append('alert inventory is not PASS')
    if delivery.get('passed') is not True: errors.append('alert delivery drill is not PASS')
    if delivery.get('alertCount') != delivery.get('observedCount'): errors.append('not every synthetic alert was observed')
    if att.get('schemaVersion') != 1: errors.append('schemaVersion must equal 1')
    expected_prometheus_rules = int(att.get('prometheusRuleAlertCount', -1))
    if expected_prometheus_rules != int(delivery.get('alertCount', -2)):
        errors.append('prometheusRuleAlertCount does not match delivery evidence')
    if int(att.get('repositoryAlertCount', -1)) != int(inventory.get('alertCount', -2)):
        errors.append('repositoryAlertCount does not match inventory')
    for key in ('pendingFiringResolvedVerified', 'receiverDeliveryVerified', 'recoveryNotificationVerified', 'runbooksOpened'):
        if att.get(key) is not True: errors.append(f'{key} must be true')
    for key in ('sreLead', 'onCallLead', 'signedAt', 'changeReference'):
        if not text(att.get(key)): errors.append(f'{key} missing or placeholder')
    report={'schemaVersion':1,'passed':not errors,'errors':errors,'inventoryCount':inventory.get('alertCount'),'delivery':delivery,'attestation':att}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(f"Phase 63G alert verification: {'PASS' if not errors else 'FAIL'}")
    for error in errors: print('  ERROR:', error)
    return 0 if not errors else 1


if __name__ == '__main__': raise SystemExit(main())
