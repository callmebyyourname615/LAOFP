#!/usr/bin/env python3
import json,subprocess,sys
src,out=sys.argv[1:]; d=json.load(open(src))
commit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
for k in ['paperRunbooksStored','offsiteVaultBackupVerified','restoreAccessTested','knowledgeTransferSigned']:
    assert d.get(k) is True,k
assert d.get('gitCommit')==commit
assert d.get('synthetic') is False
assert d.get('signers')
json.dump(d,open(out,'w'),indent=2,sort_keys=True)
