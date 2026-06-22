import hashlib,json,os,subprocess,sys,tempfile,unittest
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[3]; PY=sys.executable
NOW='2026-06-22T01:00:00Z'; CAP='2026-06-22T00:55:00Z'
class Phase57Tests(unittest.TestCase):
 def run_cmd(self,*args): return subprocess.run(args,cwd=ROOT,text=True,capture_output=True)
 def write(self,p,data): p.write_text(json.dumps(data),encoding='utf-8')
 def test_impact_mapping_and_unmapped_full(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'c.json',{'changedPaths':['src/main/java/A.java','unknown/new.file']})
   r=self.run_cmd(PY,'scripts/enterprise/detect_control_impact.py','--changes',str(t/'c.json'),'--rules','certification/enterprise/impact-rules.yaml','--full-recertification-on-unmapped','--output',str(t/'o.json'))
   self.assertEqual(r.returncode,0,r.stderr); o=json.loads((t/'o.json').read_text()); self.assertTrue(o['fullRecertificationRequired']); self.assertIn('54A',o['requiredCertifications'])
 def test_recertification_allows_complete_identity_bound_results(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); ref='rel-1'; commit='a'*40; digest='sha256:'+'b'*64
   self.write(t/'i.json',{'requiredCertifications':['54A']})
   self.write(t/'r.json',{'certifications':[{'id':'54A','status':'PASS','evidenceSha256':'c'*64,'releaseReference':ref,'gitCommit':commit,'imageDigest':digest}]})
   self.write(t/'p.json',{'status':'PASS','release':{'reference':ref,'gitCommit':commit,'imageDigest':digest},'issuedAt':'2026-06-20T00:00:00Z','expiresAt':'2026-07-20T00:00:00Z','signatureVerified':True})
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_recertification.py','--impact',str(t/'i.json'),'--results',str(t/'r.json'),'--prior-certificate',str(t/'p.json'),'--validity','certification/enterprise/certification-validity.yaml','--release-reference',ref,'--git-commit',commit,'--image-digest',digest,'--now',NOW,'--output',str(t/'o.json'),'--validity-output',str(t/'v.json'))
   self.assertEqual(r.returncode,0,r.stderr); self.assertEqual(json.loads((t/'o.json').read_text())['decision'],'ALLOW')
 def test_recertification_blocks_missing_result(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'i.json',{'requiredCertifications':['54A']}); self.write(t/'r.json',{'certifications':[]}); self.write(t/'p.json',{'status':'PASS','release':{'reference':'rel-1','gitCommit':'a'*40,'imageDigest':'sha256:'+'b'*64},'issuedAt':'2026-06-20T00:00:00Z','expiresAt':'2026-07-20T00:00:00Z','signatureVerified':True})
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_recertification.py','--impact',str(t/'i.json'),'--results',str(t/'r.json'),'--prior-certificate',str(t/'p.json'),'--validity','certification/enterprise/certification-validity.yaml','--release-reference','rel-1','--git-commit','a'*40,'--image-digest','sha256:'+'b'*64,'--now',NOW,'--output',str(t/'o.json'),'--validity-output',str(t/'v.json'))
   self.assertNotEqual(r.returncode,0); self.assertEqual(json.loads((t/'o.json').read_text())['decision'],'BLOCK')
 def dr_data(self,loss=0):
  topo={'capturedAt':CAP,'regions':[{'role':'primary'},{'role':'dr'}],'fencing':{'enabled':True,'singleWriterEnforced':True},'databaseReplicationLagSeconds':1,'kafkaReplicationLagMessages':0,'objectReplicationLagSeconds':2,'vaultReplicationLagSeconds':1,'objectStorage':{'versioningEnabled':True,'objectLockCompliance':True},'configurationParity':True,'secretsParityVerified':True,'warmCapacityPercent':25}
  fo={'capturedAt':CAP,'status':'PASS','rpoSeconds':10,'rtoSeconds':100,'dnsFailoverSeconds':30,'transactionsLost':loss,'duplicateTransactions':0,'reconciliationStatus':'PASS'}
  fb={'capturedAt':CAP,'status':'PASS','dataDivergenceCount':0,'reconciliationStatus':'PASS','approvedBy':['a','b']}; return topo,fo,fb
 def test_dr_blocks_data_loss(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); topo,fo,fb=self.dr_data(1)
   for n,d in [('s',topo),('f',fo),('b',fb)]: self.write(t/f'{n}.json',d)
   r=self.run_cmd(PY,'scripts/enterprise/verify_multi_region_dr.py','--snapshot',str(t/'s.json'),'--failover',str(t/'f.json'),'--failback',str(t/'b.json'),'--policy','multi-region-dr/topology-policy.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--topology-output',str(t/'to.json'),'--failover-output',str(t/'fo.json'),'--failback-output',str(t/'fb.json'))
   self.assertNotEqual(r.returncode,0)
 def test_financial_zero_tolerance(self):
  base={'capturedAt':CAP,'unbalancedJournals':0,'balanceMismatchMinorUnits':0,'orphanPostings':0,'duplicateReversals':0,'completedTransactionsWithoutPosting':0,'postingsWithoutTransaction':0,'oldestSuspenseAgeHours':1,'snapshotIsolation':'REPEATABLE_READ','currencyPrecisionViolations':0,'settlementMismatchMinorUnits':0,'outboxMissingForCommittedTransactions':0,'financialControllerOwner':'controller'}
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'s.json',base)
   cmd=[PY,'scripts/enterprise/evaluate_financial_controls.py','--snapshot',str(t/'s.json'),'--catalog','financial-controls/control-catalog.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')]
   self.assertEqual(self.run_cmd(*cmd).returncode,0); base['balanceMismatchMinorUnits']=1; self.write(t/'s.json',base); self.assertNotEqual(self.run_cmd(*cmd).returncode,0); self.assertEqual(json.loads((t/'d.json').read_text())['decision'],'FREEZE_AFFECTED_SETTLEMENT')
 def lifecycle_inventory(self):
  pol=yaml.safe_load((ROOT/'data-governance/retention-policy.yaml').read_text())['datasets']; return {'capturedAt':CAP,'datasets':[{'dataset':n,'classification':c['classification'],'retentionDays':c['retentionDays'],'encryptedAtRest':True,'legalHoldCount':0,'purgeEligibleCount':0,'archiveObjectCount':0,'archiveChecksumVerified':True} for n,c in pol.items()]}
 def test_legal_hold_blocks_deletion(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'i.json',self.lifecycle_inventory()); req={'mode':'DRY_RUN','requestId':'x','dataset':'applicationLogs','retentionExpired':True,'legalHoldPresent':True,'referentialIntegrityStatus':'PASS','archiveVerificationStatus':'PASS','deletionManifestSha256':'a'*64,'approvers':['a','b']}; self.write(t/'q.json',req)
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_data_lifecycle.py','--inventory',str(t/'i.json'),'--deletion-request',str(t/'q.json'),'--retention-policy','data-governance/retention-policy.yaml','--deletion-policy','data-governance/deletion-policy.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--eligibility-output',str(t/'e.json'))
   self.assertNotEqual(r.returncode,0); self.assertEqual(json.loads((t/'e.json').read_text())['status'],'BLOCKED')
 def test_fraud_critical_unassigned_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'s.json',{'capturedAt':CAP,'modelVersion':'rules-v1','entities':[{'entityId':'masked-1','signals':{'sanctionsProximityIndicators':1}}]})
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_fraud_signals.py','--snapshot',str(t/'s.json'),'--rules','fraud/rule-catalog.yaml','--scoring','fraud/risk-scoring-policy.yaml','--routing','fraud/case-routing.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--routing-output',str(t/'c.json'))
   self.assertNotEqual(r.returncode,0)
 def test_anomaly_unacknowledged_critical_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.write(t/'s.json',{'capturedAt':CAP,'season':'weekday','signals':{'kafkaConsumerLag':{'current':1000,'baseline':[1]*30,'acknowledged':False}},'suppressions':[]})
   r=self.run_cmd(PY,'scripts/enterprise/detect_observability_anomalies.py','--snapshot',str(t/'s.json'),'--policy','observability/intelligence/anomaly-policy.yaml','--seasonal-baselines','observability/intelligence/seasonal-baselines.yaml','--correlation-rules','observability/intelligence/correlation-rules.yaml','--suppression-policy','observability/intelligence/suppression-policy.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--anomaly-output',str(t/'a.json'),'--correlation-output',str(t/'c.json'))
   self.assertNotEqual(r.returncode,0)
 def test_operation_rejects_shell_injection(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); req={'operation':'restart-deployment','requesterRole':'operator','requester':'alice','approvers':['bob'],'approvalRecords':[{'approver':'bob','approvedAt':CAP,'signatureVerified':True,'evidenceSha256':'a'*64}],'parameters':{'target':'switching;rm-x'},'dryRunStatus':'PASS','namespace':'switching-prod'}; self.write(t/'q.json',req)
   r=self.run_cmd(PY,'scripts/enterprise/validate_operation_request.py','--request',str(t/'q.json'),'--catalog','operations/operation-catalog.yaml','--authorization','operations/authorization-policy.yaml','--approval-policy','operations/approval-policy.yaml','--safety-limits','operations/safety-limits.yaml','--now',NOW,'--validation-output',str(t/'v.json'),'--plan-output',str(t/'p.json'))
   self.assertNotEqual(r.returncode,0); self.assertFalse(json.loads((t/'p.json').read_text())['executionAuthorized'])
 def test_overdue_vulnerability_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); snap={'capturedAt':CAP,'findings':[{'id':'CVE-X','severity':'critical','knownExploited':True,'firstDetectedAt':'2026-06-20T00:00:00Z','status':'OPEN','owner':'sec'}],'exceptions':[],'components':[]}; self.write(t/'s.json',snap)
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_vulnerability_lifecycle.py','--snapshot',str(t/'s.json'),'--sla','vulnerability-management/severity-sla.yaml','--exception-policy','vulnerability-management/exception-policy.yaml','--supported-version-policy','vulnerability-management/supported-version-policy.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--remediation-output',str(t/'m.json'))
   self.assertNotEqual(r.returncode,0)
 def valid_dependencies(self):
  cat=yaml.safe_load((ROOT/'continuity/dependency-catalog.yaml').read_text())['dependencies']; sc=yaml.safe_load((ROOT/'continuity/scenario-catalog.yaml').read_text())['scenarios']; return {'capturedAt':CAP,'dependencies':[{'id':n,'timeoutSeconds':5,'retryPolicy':'bounded','circuitBreaker':'enabled','fallback':'documented','backlogLimit':100,'recoveryProcedure':'runbook','reconciliationRequired':True,'reconciliationStatus':'PASS','testedMode':c['transactionBehavior'],'observedOutageMinutes':1,'recoveryStatus':'PASS','circuitBreakerTested':True,'fallbackUsesStaleData':False} for n,c in cat.items()],'scenarioResults':[{'id':x['id'],'status':'PASS','observedMode':x['expectedMode']} for x in sc]}
 def test_dependency_missing_scenario_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); d=self.valid_dependencies(); d['scenarioResults']=d['scenarioResults'][:-1]; self.write(t/'s.json',d)
   r=self.run_cmd(PY,'scripts/enterprise/evaluate_dependency_resilience.py','--snapshot',str(t/'s.json'),'--catalog','continuity/dependency-catalog.yaml','--degraded-policy','continuity/degraded-mode-policy.yaml','--scenarios','continuity/scenario-catalog.yaml','--thresholds','config/phase57-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decisions-output',str(t/'d.json'))
   self.assertNotEqual(r.returncode,0)
 def test_status_respects_custom_enterprise_root(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); p=t/'phases'/'57A'; p.mkdir(parents=True); self.write(p/'result.json',{'status':'PASS'})
   env=os.environ.copy(); env['ENTERPRISE_ROOT']=str(t)
   r=subprocess.run(['scripts/enterprise/run_phase57_enterprise.sh','status'],cwd=ROOT,text=True,capture_output=True,env=env)
   self.assertEqual(r.returncode,0,r.stderr); self.assertIn('57A: PASS',r.stdout)
 def test_evidence_manifest_detects_tamper(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); (t/'a.json').write_text('{}'); r=self.run_cmd(PY,'scripts/enterprise/build_evidence_manifest.py','--root',str(t),'--output',str(t/'m.json'),'--release-reference','rel-1','--git-commit','a'*40,'--image-digest','sha256:'+'b'*64); self.assertEqual(r.returncode,0,r.stderr); (t/'a.json').write_text('{"x":1}')
   r=self.run_cmd(PY,'scripts/enterprise/verify_evidence_manifest.py','--manifest',str(t/'m.json'),'--root',str(t)); self.assertNotEqual(r.returncode,0)
 def test_maturity_certificate_passes_complete_evidence(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); er=t/'e'; ref='rel-1'; commit='a'*40; digest='sha256:'+'b'*64
   for l in 'ABCDEFGHI':
    p=er/'phases'/f'57{l}'; p.mkdir(parents=True); self.write(p/'result.json',{'phase':f'57{l}','status':'PASS','release':{'reference':ref,'gitCommit':commit,'imageDigest':digest}})
   model=yaml.safe_load((ROOT/'enterprise-certification/maturity-model.yaml').read_text()); external=sorted({str(x) for d in model['domains'].values() for x in d['requiredPhases'] if not str(x).startswith('57')})
   controls=yaml.safe_load((ROOT/'enterprise-certification/control-catalog.yaml').read_text()); inp={'capturedAt':CAP,'externalPhaseResults':[{'phase':x,'status':'PASS','releaseReference':ref,'gitCommit':commit,'imageDigest':digest,'evidenceSha256':'c'*64} for x in external],'criticalControls':{x:'PASS' for x in controls['criticalControls']+controls['highControls']},'exceptions':[],'resilienceCertificate':{'status':'PASS','signatureVerified':True,'evidenceSha256':'d'*64,'expiresAt':'2026-08-01T00:00:00Z'},'operationalOwners':['ops','security','business']}; self.write(t/'i.json',inp)
   r=self.run_cmd(PY,'scripts/enterprise/issue_enterprise_maturity_certificate.py','--enterprise-root',str(er),'--input',str(t/'i.json'),'--model','enterprise-certification/maturity-model.yaml','--controls','enterprise-certification/control-catalog.yaml','--policy','enterprise-certification/certification-policy.yaml','--thresholds','config/phase57-thresholds.yaml','--release-reference',ref,'--git-commit',commit,'--image-digest',digest,'--now',NOW,'--score-output',str(t/'s.json'),'--controls-output',str(t/'c.json'),'--certificate-output',str(t/'cert.json'))
   self.assertEqual(r.returncode,0,r.stderr); self.assertEqual(json.loads((t/'cert.json').read_text())['decision'],'CERTIFIED')
if __name__=='__main__': unittest.main()
