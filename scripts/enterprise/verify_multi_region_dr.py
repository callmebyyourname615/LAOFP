#!/usr/bin/env python3
import argparse
from lib import load,write,ensure_fresh,now_utc
p=argparse.ArgumentParser(); p.add_argument('--snapshot',required=True); p.add_argument('--failover',required=True); p.add_argument('--failback',required=True); p.add_argument('--policy',required=True); p.add_argument('--thresholds',required=True); p.add_argument('--now'); p.add_argument('--topology-output',required=True); p.add_argument('--failover-output',required=True); p.add_argument('--failback-output',required=True); a=p.parse_args()
s=load(a.snapshot); fo=load(a.failover); fb=load(a.failback); policy=load(a.policy); th=load(a.thresholds); now=now_utc(a.now); errors=[]
max_age=th['freshness']['defaultSnapshotMaxAgeSeconds']
for name,d in [('topology',s),('failover',fo),('failback',fb)]:
    try: ensure_fresh(d,max_age,now)
    except Exception as e: errors.append(f'{name}: {e}')
regions=s.get('regions',[])
if len(regions)<policy.get('minimumRegionCount',2): errors.append('fewer than two regions')
roles=[x.get('role') for x in regions]
if roles.count('primary')!=1 or roles.count('dr')<1: errors.append('exactly one primary and at least one dr region required')
if not s.get('fencing',{}).get('enabled') or not s.get('fencing',{}).get('singleWriterEnforced'): errors.append('region fencing/single writer not enforced')
mr=th['multiRegion']
checks={
'databaseReplicationLagSeconds':mr['maximumDatabaseReplicationLagSeconds'],
'kafkaReplicationLagMessages':mr['maximumKafkaReplicationLagMessages'],
'objectReplicationLagSeconds':mr['maximumObjectReplicationLagSeconds'],
'vaultReplicationLagSeconds':mr['maximumVaultReplicationLagSeconds'],
}
for key,limit in checks.items():
    value=s.get(key)
    if value is None or value>limit: errors.append(f'{key} exceeds threshold or missing')
if not s.get('objectStorage',{}).get('versioningEnabled') or not s.get('objectStorage',{}).get('objectLockCompliance'): errors.append('DR object storage versioning/Object Lock COMPLIANCE required')
if not s.get('configurationParity') or not s.get('secretsParityVerified'): errors.append('configuration/secrets parity not verified')
if s.get('warmCapacityPercent',0)<policy['components']['application']['warmCapacityMinimumPercent']: errors.append('DR warm capacity below policy')
fo_errors=[]
if fo.get('status')!='PASS': fo_errors.append('failover drill status not PASS')
if fo.get('rpoSeconds',10**9)>mr['maximumRpoSeconds']: fo_errors.append('failover RPO exceeded')
if fo.get('rtoSeconds',10**9)>mr['maximumRtoSeconds']: fo_errors.append('failover RTO exceeded')
if fo.get('dnsFailoverSeconds',10**9)>mr['maximumDnsFailoverSeconds']: fo_errors.append('DNS failover exceeded')
if fo.get('transactionsLost',1)!=0 or fo.get('duplicateTransactions',1)!=0: fo_errors.append('data loss or duplicate transaction detected')
if fo.get('reconciliationStatus')!='PASS': fo_errors.append('post-failover reconciliation failed')
fb_errors=[]
if fb.get('status')!='PASS': fb_errors.append('failback status not PASS')
if fb.get('dataDivergenceCount',1)!=0: fb_errors.append('failback data divergence detected')
if fb.get('reconciliationStatus')!='PASS': fb_errors.append('post-failback reconciliation failed')
if not fb.get('approvedBy') or len(set(fb.get('approvedBy',[])))<2: fb_errors.append('failback requires two distinct approvers')
write(a.topology_output,{'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','errors':errors,'regionCount':len(regions),'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.failover_output,{'schemaVersion':1,'status':'PASS' if not fo_errors else 'FAIL','errors':fo_errors,'rpoSeconds':fo.get('rpoSeconds'),'rtoSeconds':fo.get('rtoSeconds'),'evaluatedAt':now.isoformat().replace('+00:00','Z')})
write(a.failback_output,{'schemaVersion':1,'status':'PASS' if not fb_errors else 'FAIL','errors':fb_errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')})
all_errors=errors+fo_errors+fb_errors
if all_errors: raise SystemExit('\n'.join(all_errors))
