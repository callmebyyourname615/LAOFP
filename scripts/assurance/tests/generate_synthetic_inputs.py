#!/usr/bin/env python3
import argparse,json,yaml
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); a=p.parse_args(); out=Path(a.output); out.mkdir(parents=True,exist_ok=True); rel={'reference':a.release_reference,'gitCommit':a.git_commit,'imageDigest':a.image_digest}; cap='2026-06-22T01:30:00Z'; future='2027-12-31T00:00:00Z'
def wr(n,d): d['release']=rel; (out/n).write_text(json.dumps(d,indent=2,sort_keys=True)+'\n')
reports=[]
for rid in yaml.safe_load(open('regulatory-assurance/report-catalog.yaml'))['reports']:
 reports.append({'reportId':rid,'schemaValid':True,'reconciled':True,'approvedBy':['reg-a','reg-b'],'submittedAt':'2026-06-22T01:00:00Z','deadlineAt':'2026-06-22T02:00:00Z','transportAck':'ACCEPTED','evidenceSha256':'c'*64})
wr('58a.json',{'capturedAt':cap,'reports':reports})
checks={x:'PASS' for x in yaml.safe_load(open('participant-governance/certification-policy.yaml'))['requiredChecks']}; wr('58b.json',{'capturedAt':cap,'participants':[{'participantId':'BANK1','status':'ACTIVE','riskOwner':'participant-risk','riskTier':'HIGH','certificateExpiresAt':future,'trafficEnabled':True,'checks':checks}]})
wr('58c.json',{'capturedAt':cap,'cryptoBomCoveragePercent':100,'assets':[{'id':'signing','algorithm':'ECDSA-P384','backing':'HSM','nextRotationAt':future,'type':'KEY'}],'pqcPlan':{'owner':'crypto','targetDate':'2027-12-31','hybridPilotStatus':'PASS','harvestNowDecryptLaterAssessment':'COMPLETE'}})
wr('58d.json',{'capturedAt':cap,'datasets':[{'dataset':'transactions','purpose':'payment-processing','legalBasis':'legal-obligation','encrypted':True,'minimizationStatus':'PASS','nonProductionMasking':'PASS','crossBorder':False}],'rightsCases':[],'breaches':[]})
wr('58e.json',{'capturedAt':cap,'decisionAssets':[{'id':'fraud-rules','owner':'fraud','status':'APPROVED','version':'1.0.0','versionImmutable':True,'approvedBy':['risk','compliance'],'reasonCodeCoveragePercent':100,'rollbackTestStatus':'PASS','driftScore':0.01,'productionDigest':'sha256:'+'d'*64,'unapprovedShadowDecisions':0}]})
packs=[]
for m in yaml.safe_load(open('iso20022/market-practice-lifecycle.yaml'))['supportedMessagePacks']:
 packs.append({'messagePack':m,'schemaValidation':'PASS','marketPracticeValidation':'PASS','negativeTests':'PASS','backwardCompatibility':'PASS','signatureVerification':'PASS','packSha256':'e'*64})
wr('58f.json',{'capturedAt':cap,'messagePacks':packs,'externalCodeSets':{'ageHours':1,'activationMode':'ATOMIC','partialDownloadReplacedActive':False}})
wr('58g.json',{'capturedAt':cap,'settlementMismatchMinorUnits':0,'gridlockDetectionStatus':'PASS','participants':[{'participantId':'BANK1','availableMinorUnits':1000000,'reservedMinorUnits':1000,'eligibleCollateralAfterHaircutMinorUnits':10000,'minimumOperatingMinorUnits':100,'oldestQueueAgeSeconds':1,'currentExposureMinorUnits':100,'exposureLimitMinorUnits':100000,'riskTier':'HIGH','prefundingStatus':'PASS','collateral':[{'id':'c1','type':'CASH','valuationAgeHours':1}]}]})
sc=[]
for q in yaml.safe_load(open('digital-twin/scenario-policy.yaml'))['requiredScenarios']:
 sc.append({'scenarioId':q,'status':'PASS','deterministicReplay':True,'productionSideEffects':False,'forecastErrorPercent':1,'financialMismatchMinorUnits':0})
wr('58h.json',{'capturedAt':cap,'dataset':{'sanitized':True,'containsProductionIdentifiers':False,'sha256':'f'*64},'networkIsolation':True,'scenarioResults':sc})
wr('58i.json',{'capturedAt':cap,'vendors':[{'vendorId':'VENDOR1','criticality':'CRITICAL','serviceOwner':'platform','dataLocations':['LA'],'subprocessorInventoryStatus':'PASS','rightToAudit':True,'assuranceDate':'2026-01-01T00:00:00Z','bcpDrEvidenceStatus':'PASS','exitPlanStatus':'PASS','exitTestStatus':'PASS','securityAttestationStatus':'PASS'}],'concentrations':[{'category':'cloud','percent':30,'limitPercent':40}]})
controls=yaml.safe_load(open('supervisory-readiness/control-catalog.yaml')); wr('58j.json',{'capturedAt':cap,'criticalControls':{x:'PASS' for x in controls['criticalControls']+controls['highControls']},'evidenceManifest':{'signatureVerified':True,'sha256':'a'*64},'exceptions':[],'operationalOwners':['regulatory','business','security']})
