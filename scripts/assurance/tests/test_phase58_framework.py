#!/usr/bin/env python3
import unittest,tempfile,subprocess,json,os,yaml
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]; PY='python3'; NOW='2026-06-22T02:00:00Z'; CAP='2026-06-22T01:30:00Z'; REF='rel-58'; COM='a'*40; DIG='sha256:'+'b'*64
class T(unittest.TestCase):
 def run_cmd(self,*args): return subprocess.run(args,cwd=ROOT,text=True,capture_output=True)
 def wr(self,p,d): p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d))
 def ident(self,d): d['release']={'reference':REF,'gitCommit':COM,'imageDigest':DIG}; return d
 def test_static(self): self.assertEqual(self.run_cmd(PY,'scripts/verify_phase58_static.py').returncode,0)
 def test_regulatory_missing_report_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'reports':[]})); r=self.run_cmd(PY,'scripts/assurance/evaluate_regulatory_reporting.py','--snapshot',str(t/'s.json'),'--catalog','regulatory-assurance/report-catalog.yaml','--policy','regulatory-assurance/submission-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0)
 def test_participant_expired_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); x=self.ident({'capturedAt':CAP,'participants':[{'participantId':'BANK1','status':'ACTIVE','riskOwner':'o','riskTier':'HIGH','certificateExpiresAt':'2026-06-23T00:00:00Z','checks':{q:'PASS' for q in yaml.safe_load((ROOT/'participant-governance/certification-policy.yaml').read_text())['requiredChecks']}}]}); self.wr(t/'s.json',x); r=self.run_cmd(PY,'scripts/assurance/evaluate_participant_governance.py','--snapshot',str(t/'s.json'),'--policy','participant-governance/certification-policy.yaml','--risk-policy','participant-governance/risk-tier-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0)
 def test_prohibited_crypto_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'cryptoBomCoveragePercent':100,'assets':[{'id':'x','algorithm':'SHA-1','backing':'HSM','nextRotationAt':'2027-01-01T00:00:00Z'}],'pqcPlan':{'owner':'s','targetDate':'2027-01-01','hybridPilotStatus':'PLANNED','harvestNowDecryptLaterAssessment':'COMPLETE'}})); r=self.run_cmd(PY,'scripts/assurance/evaluate_crypto_agility.py','--snapshot',str(t/'s.json'),'--algorithm-policy','crypto-agility/algorithm-policy.yaml','--pqc-policy','crypto-agility/pqc-readiness-policy.yaml','--key-policy','crypto-agility/key-lifecycle-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--plan-output',str(t/'p.json')); self.assertNotEqual(r.returncode,0)
 def test_privacy_overdue_case_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'datasets':[],'rightsCases':[{'caseId':'c','status':'OPEN','dueAt':'2026-06-21T00:00:00Z'}],'breaches':[]})); r=self.run_cmd(PY,'scripts/assurance/evaluate_privacy_engineering.py','--snapshot',str(t/'s.json'),'--control-policy','privacy-engineering/privacy-control-policy.yaml','--rights-policy','privacy-engineering/rights-case-policy.yaml','--breach-policy','privacy-engineering/breach-notification-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0)
 def test_model_drift_rolls_back(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'decisionAssets':[{'id':'m','owner':'o','status':'APPROVED','version':'1','versionImmutable':True,'approvedBy':['a','b'],'reasonCodeCoveragePercent':100,'rollbackTestStatus':'PASS','driftScore':0.9,'productionDigest':DIG,'unapprovedShadowDecisions':0}]})); r=self.run_cmd(PY,'scripts/assurance/evaluate_decision_governance.py','--snapshot',str(t/'s.json'),'--model-policy','decision-governance/model-rule-policy.yaml','--change-policy','decision-governance/change-policy.yaml','--monitoring-policy','decision-governance/monitoring-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0); self.assertIn('ROLLBACK',json.loads((t/'d.json').read_text())['decision'])
 def test_iso_missing_pack_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'messagePacks':[],'externalCodeSets':{'ageHours':1,'activationMode':'ATOMIC','partialDownloadReplacedActive':False}})); r=self.run_cmd(PY,'scripts/assurance/evaluate_iso20022_lifecycle.py','--snapshot',str(t/'s.json'),'--lifecycle-policy','iso20022/market-practice-lifecycle.yaml','--change-policy','iso20022/change-governance.yaml','--code-set-policy','iso20022/code-set-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0)
 def test_liquidity_breach_holds_outbound(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'settlementMismatchMinorUnits':0,'gridlockDetectionStatus':'PASS','participants':[{'participantId':'B','availableMinorUnits':0,'reservedMinorUnits':1,'eligibleCollateralAfterHaircutMinorUnits':0,'minimumOperatingMinorUnits':0,'oldestQueueAgeSeconds':1,'currentExposureMinorUnits':0,'exposureLimitMinorUnits':1,'riskTier':'STANDARD','collateral':[]}]})); r=self.run_cmd(PY,'scripts/assurance/evaluate_settlement_risk.py','--snapshot',str(t/'s.json'),'--liquidity-policy','settlement-risk/liquidity-risk-policy.yaml','--collateral-policy','settlement-risk/collateral-policy.yaml','--exposure-policy','settlement-risk/exposure-limit-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0); self.assertEqual(json.loads((t/'d.json').read_text())['decision'],'HOLD_NEW_OUTBOUND')
 def test_digital_twin_production_side_effect_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); req=yaml.safe_load((ROOT/'digital-twin/scenario-policy.yaml').read_text())['requiredScenarios']; rs=[{'scenarioId':x,'status':'PASS','deterministicReplay':True,'productionSideEffects':False,'forecastErrorPercent':0,'financialMismatchMinorUnits':0} for x in req]; rs[0]['productionSideEffects']=True; self.wr(t/'s.json',self.ident({'capturedAt':CAP,'dataset':{'sanitized':True,'containsProductionIdentifiers':False,'sha256':'c'*64},'networkIsolation':True,'scenarioResults':rs})); r=self.run_cmd(PY,'scripts/assurance/evaluate_digital_twin.py','--snapshot',str(t/'s.json'),'--scenario-policy','digital-twin/scenario-policy.yaml','--data-policy','digital-twin/data-policy.yaml','--acceptance-policy','digital-twin/acceptance-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--certificate-output',str(t/'c.json')); self.assertNotEqual(r.returncode,0)
 def test_vendor_concentration_fails(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); self.wr(t/'s.json',self.ident({'capturedAt':CAP,'vendors':[],'concentrations':[{'category':'cloud','percent':80,'limitPercent':40}]})); r=self.run_cmd(PY,'scripts/assurance/evaluate_third_party_risk.py','--snapshot',str(t/'s.json'),'--vendor-policy','third-party-risk/vendor-risk-policy.yaml','--concentration-policy','third-party-risk/concentration-policy.yaml','--monitoring-policy','third-party-risk/continuous-monitoring-policy.yaml','--thresholds','config/phase58-thresholds.yaml','--now',NOW,'--report-output',str(t/'r.json'),'--decision-output',str(t/'d.json')); self.assertNotEqual(r.returncode,0)
 def test_manifest_tamper_detected(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); (t/'a').write_text('x'); self.assertEqual(self.run_cmd(PY,'scripts/assurance/build_evidence_manifest.py','--root',str(t),'--output',str(t/'m.json'),'--release-reference',REF,'--git-commit',COM,'--image-digest',DIG).returncode,0); (t/'a').write_text('y'); self.assertNotEqual(self.run_cmd(PY,'scripts/assurance/verify_evidence_manifest.py','--manifest',str(t/'m.json'),'--root',str(t)).returncode,0)
 def test_manifest_excludes_volatile_runner_metadata(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); (t/'stable.json').write_text('{}'); (t/'checks.jsonl').write_text('before'); (t/'logs').mkdir(); (t/'logs'/'x.log').write_text('before')
   self.assertEqual(self.run_cmd(PY,'scripts/assurance/build_evidence_manifest.py','--root',str(t),'--output',str(t/'m.json'),'--release-reference',REF,'--git-commit',COM,'--image-digest',DIG).returncode,0)
   (t/'checks.jsonl').write_text('after'); (t/'logs'/'x.log').write_text('after')
   self.assertEqual(self.run_cmd(PY,'scripts/assurance/verify_evidence_manifest.py','--manifest',str(t/'m.json'),'--root',str(t)).returncode,0)
 def test_secret_scanner_detects_private_key_without_leaking_value(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); (t/'x.txt').write_text('-----BEGIN ' + 'PRIVATE KEY-----\nabc\n-----END ' + 'PRIVATE KEY-----'); (t/'m.txt').write_text('x.txt\n')
   r=self.run_cmd(PY,'scripts/security/scan_repository_secrets.py','--path',str(t),'--mode','manifest','--manifest',str(t/'m.txt'))
   self.assertNotEqual(r.returncode,0); self.assertIn('private-key',r.stderr); self.assertNotIn('abc',r.stderr)
 def test_secret_scanner_allows_placeholders(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); (t/'x.txt').write_text('DB_PASSWORD=${DB_PASSWORD:?required}\n'); (t/'m.txt').write_text('x.txt\n')
   self.assertEqual(self.run_cmd(PY,'scripts/security/scan_repository_secrets.py','--path',str(t),'--mode','manifest','--manifest',str(t/'m.txt')).returncode,0)
 def test_status_custom_root(self):
  with tempfile.TemporaryDirectory() as td:
   t=Path(td); p=t/'phases'/'58A'; p.mkdir(parents=True); self.wr(p/'result.json',{'status':'PASS'}); env=os.environ.copy(); env['ASSURANCE_ROOT']=str(t); r=subprocess.run(['scripts/assurance/run_phase58_assurance.sh','status'],cwd=ROOT,text=True,capture_output=True,env=env); self.assertEqual(r.returncode,0); self.assertIn('58A: PASS',r.stdout)
if __name__=='__main__': unittest.main()
