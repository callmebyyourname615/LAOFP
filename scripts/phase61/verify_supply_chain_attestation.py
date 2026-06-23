#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
import yaml
DIGEST=re.compile(r'^sha256:[0-9a-f]{64}$'); SHA=re.compile(r'^[0-9a-f]{64}$'); PLACEHOLDER=re.compile(r'(?i)(change_me|replace|todo|tbd)')

def text(v): return isinstance(v,str) and bool(v.strip()) and not PLACEHOLDER.search(v)
def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--attestation',type=Path,required=True); p.add_argument('--inventory',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
 data=json.loads(a.attestation.read_text(encoding='utf-8')); inventory=yaml.safe_load(a.inventory.read_text(encoding='utf-8')) or {}; errors=[]
 if data.get('schemaVersion')!=1: errors.append('schemaVersion must equal 1')
 expected=[x['id'] for x in inventory.get('credentials',[])]
 rotated=data.get('rotatedCredentials') or []
 if sorted(rotated)!=sorted(expected): errors.append('rotated credential set does not match inventory')
 for key in ('oldCredentialsDisabled','gitHistoryPurged','ciCachesInvalidated','oldClonesInvalidated','allBranchesAndTagsScanned'):
  if data.get(key) is not True: errors.append(f'{key} must be true')
 artifacts=data.get('artifacts') or {}
 for key in ('applicationImageDigest','migrationImageDigest'):
  if not DIGEST.fullmatch(str(artifacts.get(key,''))): errors.append(f'{key} must be digest-pinned')
 for key in ('sbomSha256','provenanceSha256'):
  if not SHA.fullmatch(str(artifacts.get(key,''))): errors.append(f'{key} must be SHA-256')
 for key in ('gitleaksPassed','trivyPassed','grypePassed','cosignVerified','provenanceVerified','manifestsDigestPinned'):
  if artifacts.get(key) is not True: errors.append(f'artifacts.{key} must be true')
 if int(artifacts.get('criticalVulnerabilities',-1))!=0 or int(artifacts.get('highVulnerabilities',-1))!=0: errors.append('Critical/High vulnerabilities must be zero or formally waived outside this gate')
 for key in ('repoCoordinator','securityLead','signedAt','changeReference'):
  if not text(data.get(key)): errors.append(f'{key} missing or placeholder')
 report={'schemaVersion':1,'passed':not errors,'errors':errors}
 a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(f"Phase 61F supply-chain attestation: {'PASS' if not errors else 'FAIL'}")
 for e in errors: print('  ERROR:',e)
 return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
