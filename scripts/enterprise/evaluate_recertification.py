#!/usr/bin/env python3
import argparse,datetime as dt
from lib import load,write,now_utc,parse_time
p=argparse.ArgumentParser(); p.add_argument('--impact',required=True); p.add_argument('--results',required=True); p.add_argument('--prior-certificate',required=True); p.add_argument('--validity',required=True); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); p.add_argument('--now'); p.add_argument('--output',required=True); p.add_argument('--validity-output',required=True); a=p.parse_args()
impact=load(a.impact); results=load(a.results); prior=load(a.prior_certificate); validity=load(a.validity); now=now_utc(a.now); errors=[]
required=set(impact.get('requiredCertifications',[])); rows={str(x.get('id')):x for x in results.get('certifications',[])}
expected=(a.release_reference,a.git_commit,a.image_digest)
for cid in sorted(required):
    row=rows.get(cid)
    if not row: errors.append(f'missing certification result: {cid}'); continue
    if row.get('status')!='PASS': errors.append(f'certification not PASS: {cid}')
    if (row.get('releaseReference'),row.get('gitCommit'),row.get('imageDigest'))!=expected: errors.append(f'certification release identity mismatch: {cid}')
    if not row.get('evidenceSha256') or len(row['evidenceSha256'])!=64: errors.append(f'certification evidence hash missing: {cid}')
if prior.get('status')!='PASS': errors.append('prior enterprise/day2 certificate is not PASS')
if (prior.get('release',{}).get('reference'),prior.get('release',{}).get('gitCommit'),prior.get('release',{}).get('imageDigest'))!=expected: errors.append('prior certificate release identity mismatch')
expires=prior.get('expiresAt')
if not expires or parse_time(expires)<=now: errors.append('prior certificate expired')
if not prior.get('signatureVerified',False): errors.append('prior certificate signature not verified')
issued=prior.get('issuedAt')
max_days=validity.get('validity',{}).get('resilience',{}).get('days',90)
if issued and (now-parse_time(issued)).total_seconds()>max_days*86400: errors.append('prior resilience certificate exceeds validity policy')
decision='ALLOW' if not errors else 'BLOCK'
out={'schemaVersion':1,'decision':decision,'requiredCertifications':sorted(required),'evaluatedCertifications':[rows[x] for x in sorted(required) if x in rows],'errors':errors,'evaluatedAt':now.isoformat().replace('+00:00','Z')}
write(a.output,out)
valid_until=(now+dt.timedelta(days=min(30,max_days))).isoformat().replace('+00:00','Z')
write(a.validity_output,{'schemaVersion':1,'status':'VALID' if decision=='ALLOW' else 'INVALID','validFrom':now.isoformat().replace('+00:00','Z'),'validUntil':valid_until,'invalidatedByUnmappedChange':bool(impact.get('unmappedPaths')) and not impact.get('fullRecertificationRequired')})
if errors: raise SystemExit('\n'.join(errors))
