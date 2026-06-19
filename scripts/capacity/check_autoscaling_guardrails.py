#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main(policy_path,hpa_path):
    policy=yaml.safe_load(pathlib.Path(policy_path).read_text(encoding='utf-8'))
    hpa=yaml.safe_load(pathlib.Path(hpa_path).read_text(encoding='utf-8'))
    spec=hpa.get('spec',{}); failures=[]
    if spec.get('minReplicas',0)<policy['min_replicas']: failures.append('minReplicas below governed minimum')
    if spec.get('maxReplicas',0)>policy['max_replicas']: failures.append('maxReplicas above governed maximum')
    if spec.get('maxReplicas',0)<policy.get('forecast_required_replicas',1): failures.append('maxReplicas below latest forecast')
    behavior=spec.get('behavior',{}); down=(behavior.get('scaleDown') or {}).get('stabilizationWindowSeconds',0)
    if down<policy['minimum_scale_down_stabilization_seconds']: failures.append('scaleDown stabilization below policy')
    print(json.dumps({'decision':'DENY' if failures else 'ALLOW','failures':failures},sort_keys=True))
    return 2 if failures else 0
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('policy');p.add_argument('hpa');a=p.parse_args();raise SystemExit(main(a.policy,a.hpa))
