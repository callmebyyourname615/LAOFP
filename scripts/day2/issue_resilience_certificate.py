#!/usr/bin/env python3
import argparse,datetime,hashlib,json,pathlib,sys,yaml,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--results',required=True);p.add_argument('--policy',required=True);p.add_argument('--thresholds',required=True);p.add_argument('--catalog',required=True);p.add_argument('--release-reference',required=True);p.add_argument('--git-commit',required=True);p.add_argument('--image-digest',required=True);p.add_argument('--output',required=True);a=p.parse_args()
 r=json.loads(pathlib.Path(a.results).read_text());pol=yaml.safe_load(pathlib.Path(a.policy).read_text());t=yaml.safe_load(pathlib.Path(a.thresholds).read_text());catalog=yaml.safe_load(pathlib.Path(a.catalog).read_text());expected={x['id']:x for x in catalog['scenarios']};actual={x.get('id'):x for x in r.get('scenarios',[])};fail=[]
 required=['status','rtoSeconds','rpoSeconds','dataLossCount','duplicateReplayCount','reconciliationStatus','alertDeliveryStatus','undocumentedRecoverySteps']
 for sid,limit in expected.items():
  s=actual.get(sid)
  if not s:fail.append(sid+':missing');continue
  if any(k not in s for k in required):fail.append(sid+':missing-fields');continue
  if s['status']!='PASS' or s['rtoSeconds']>limit['rtoSeconds'] or s['rpoSeconds']>limit['rpoSeconds'] or s['dataLossCount']>t['maximumDataLossCount'] or s['duplicateReplayCount']>t['maximumDuplicateReplayCount'] or s['reconciliationStatus']!='PASS' or s['alertDeliveryStatus']!='PASS' or s['undocumentedRecoverySteps']>t['maximumUndocumentedRecoverySteps']:fail.append(sid)
 extra=sorted(set(actual)-set(expected));
 if extra:fail.extend('unexpected:'+x for x in extra)
 if fail:pathlib.Path(a.output).write_text(json.dumps({'schemaVersion':1,'status':'NOT_CERTIFIED','failures':fail},indent=2,sort_keys=True)+'\n');return 1
 now=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0);valid=now+datetime.timedelta(days=pol['certificateValidityDays']);digest=hashlib.sha256(pathlib.Path(a.results).read_bytes()).hexdigest();doc={'schemaVersion':1,'certificateId':'res-'+uuid.uuid4().hex,'issuedAt':now.isoformat().replace('+00:00','Z'),'validUntil':valid.isoformat().replace('+00:00','Z'),'release':{'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest},'status':'CERTIFIED','scenarioResults':[actual[x] for x in expected],'evidenceSha256':digest};pathlib.Path(a.output).write_text(json.dumps(doc,indent=2,sort_keys=True)+'\n');return 0
if __name__=='__main__':sys.exit(main())
