#!/usr/bin/env python3
import argparse,json,pathlib,sys,yaml

def main():
 p=argparse.ArgumentParser();p.add_argument('--snapshot',required=True);p.add_argument('--policy',required=True);p.add_argument('--output',required=True);a=p.parse_args();s=json.loads(pathlib.Path(a.snapshot).read_text());pol=yaml.safe_load(pathlib.Path(a.policy).read_text());checks=[]
 def chk(i,ok,actual,expected):checks.append({'id':i,'status':'PASS' if ok else 'FAIL','actual':actual,'expected':expected})
 ap=pol['application']; chk('application-replicas',s['application']['readyReplicas']>=ap['minimumReplicas'],s['application']['readyReplicas'],ap['minimumReplicas']);chk('zone-spread',s['application']['zones']>=2,s['application']['zones'],'>=2');chk('pdb',s['application']['pdbMinAvailable']>=ap['podDisruptionBudget']['minAvailable'],s['application']['pdbMinAvailable'],ap['podDisruptionBudget']['minAvailable'])
 db=pol['postgresql'];chk('db-replication-lag',s['database']['replicationLagSeconds']<=db['maximumReplicationLagSeconds'],s['database']['replicationLagSeconds'],db['maximumReplicationLagSeconds']);chk('db-single-primary',s['database']['primaryCount']==1,s['database']['primaryCount'],1);chk('db-fencing',bool(s['database']['fencingEnabled']),s['database']['fencingEnabled'],True)
 k=pol['kafka'];chk('kafka-brokers',s['kafka']['brokers']>=k['minimumBrokers'],s['kafka']['brokers'],k['minimumBrokers']);chk('under-replicated-partitions',s['kafka']['underReplicatedPartitions']<=k['maximumUnderReplicatedPartitions'],s['kafka']['underReplicatedPartitions'],k['maximumUnderReplicatedPartitions']);chk('min-isr',s['kafka']['minimumInSyncReplicas']>=k['minimumInSyncReplicas'],s['kafka']['minimumInSyncReplicas'],k['minimumInSyncReplicas'])
 status='PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL';pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'status':status,'checks':checks},indent=2,sort_keys=True)+'\n');return 0 if status=='PASS' else 1
if __name__=='__main__':sys.exit(main())
