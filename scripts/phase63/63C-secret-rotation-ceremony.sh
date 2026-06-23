#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63C 'Secret rotation, history purge and supply-chain ceremony verification'
STATUS=FAIL; MESSAGE='secret rotation ceremony verification failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file security/rotation/phase61-supply-chain-inventory.yaml
phase63_require_file scripts/phase61/verify_supply_chain_attestation.py
phase63_require_file security/scripts/verify_repository_hygiene.py
phase63_require_file docs/phase63/security/PHASE63_SECRET_ROTATION_RUNBOOK.md
if phase63_is_preflight; then
  STATUS=PREPARED; MESSAGE='rotation ceremony, history purge and supply-chain evidence contracts are ready; repository secrets were not scanned in safe preflight'; exit 0
fi
if phase63_is_repo; then
  phase63_run 'repository hygiene readiness' python3 security/scripts/verify_repository_hygiene.py --allow-pending-deletions
  STATUS=PREPARED; MESSAGE='repository checks passed; operator rotation and history rewrite remain unsigned'; exit 0
fi
phase63_require_uat_confirmation
: "${PHASE63_SUPPLY_CHAIN_ATTESTATION:?PHASE63_SUPPLY_CHAIN_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_SUPPLY_CHAIN_ATTESTATION"
phase63_run 'strict repository hygiene' python3 security/scripts/verify_repository_hygiene.py
phase63_run 'signed supply-chain and rotation attestation' python3 scripts/phase61/verify_supply_chain_attestation.py \
  --attestation "$PHASE63_SUPPLY_CHAIN_ATTESTATION" \
  --inventory security/rotation/phase61-supply-chain-inventory.yaml \
  --output "$PHASE63_PHASE_DIR/supply-chain-verification.json"
if git log --all --oneline --grep='change_me\|CHANGE_ME' > "$PHASE63_PHASE_DIR/git-history-placeholder-search.txt"; then :; fi
[[ ! -s "$PHASE63_PHASE_DIR/git-history-placeholder-search.txt" ]] || { phase63_log 'ERROR placeholder history remains'; exit 1; }
STATUS=PASS; MESSAGE='signed six-secret rotation, history purge and immutable supply-chain evidence verified'
