#!/usr/bin/env python3
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, pathlib

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--business',required=True); ap.add_argument('--operations',required=True); ap.add_argument('--security',required=True)
    ap.add_argument('--known-issues',required=True); ap.add_argument('--reference',required=True); ap.add_argument('--rc-id',required=True)
    ap.add_argument('--git-commit',required=True); ap.add_argument('--hypercare-summary',required=True); ap.add_argument('--post-implementation-review',required=True); ap.add_argument('--output',required=True)
    a=ap.parse_args(); errors=[]; acceptances={}
    def sha(path): return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
    expected_hashes={'hypercareSummarySha256':sha(a.hypercare_summary),'knownIssuesSha256':sha(a.known_issues),'postImplementationReviewSha256':sha(a.post_implementation_review)}
    for name,path in [('business',a.business),('operations',a.operations),('security',a.security)]:
        doc=json.loads(pathlib.Path(path).read_text()); acceptances[name]=doc
        if doc.get('decision')!='ACCEPT': errors.append(f'{name} acceptance decision is not ACCEPT')
        if doc.get('releaseReference')!=a.reference or doc.get('releaseCandidateId')!=a.rc_id or doc.get('gitCommit')!=a.git_commit: errors.append(f'{name} acceptance identity mismatch')
        if len({x for x in doc.get('approvedBy',[]) if x})<2: errors.append(f'{name} acceptance requires two approvers')
        for key,value in expected_hashes.items():
            if doc.get(key)!=value: errors.append(f'{name} acceptance evidence hash mismatch: {key}')
        try: dt.datetime.fromisoformat(doc.get('acceptedAt','').replace('Z','+00:00'))
        except Exception: errors.append(f'{name} acceptedAt invalid')
    issues=json.loads(pathlib.Path(a.known_issues).read_text()); open_critical=[]; invalid_high=[]
    for issue in issues.get('issues',[]):
        if issue.get('status') in ('CLOSED','RESOLVED'): continue
        severity=str(issue.get('severity','')).upper()
        if severity=='CRITICAL': open_critical.append(issue.get('id'))
        if severity=='HIGH' and (not issue.get('owner') or not issue.get('targetDate') or len({x for x in issue.get('riskAcceptedBy',[]) if x})<2): invalid_high.append(issue.get('id'))
    if open_critical: errors.append('open critical known issues exist')
    if invalid_high: errors.append('open high issues lack owner, target date, or two-person risk acceptance')
    report={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','releaseReference':a.reference,'releaseCandidateId':a.rc_id,'acceptances':{k:{'decision':v.get('decision'),'acceptedAt':v.get('acceptedAt'),'approvedBy':v.get('approvedBy',[])} for k,v in acceptances.items()},'openCriticalIssues':open_critical,'invalidHighIssues':invalid_high,'errors':errors}
    pathlib.Path(a.output).write_text(json.dumps(report,indent=2,sort_keys=True)+'\n'); print(json.dumps({'status':report['status'],'errors':len(errors)})); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
