#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55F; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A 55B 55C 55D 55E
: "${CUTOVER_PLAN_FILE:?CUTOVER_PLAN_FILE required}"; : "${CONTACT_MATRIX_ATTESTATION_FILE:?CONTACT_MATRIX_ATTESTATION_FILE required}"
: "${COMMAND_CENTER_APPROVAL_FILE:?COMMAND_CENTER_APPROVAL_FILE required}"
: "${COMMAND_CENTER_APPROVAL_SIGNATURE:?COMMAND_CENTER_APPROVAL_SIGNATURE required}"
: "${DECISION_PUBLIC_KEY:?DECISION_PUBLIC_KEY required}"
for file in "$CUTOVER_PLAN_FILE" "$CONTACT_MATRIX_ATTESTATION_FILE" "$COMMAND_CENTER_APPROVAL_FILE" "$COMMAND_CENTER_APPROVAL_SIGNATURE"; do [[ -f "$file" && ! -L "$file" ]] || live_die "command-center input must be a regular non-symlink file: $file"; done
live_require_command cosign
phase_begin 55F "Go-Live Command Center"
failed=0
cp "$CUTOVER_PLAN_FILE" "$PHASE_DIR/approved-cutover-plan.json"
cp "$CONTACT_MATRIX_ATTESTATION_FILE" "$PHASE_DIR/contact-matrix-attestation.json"
cp "$COMMAND_CENTER_APPROVAL_FILE" "$PHASE_DIR/command-center-approval.json"
cp "$COMMAND_CENTER_APPROVAL_SIGNATURE" "$PHASE_DIR/command-center-approval.sig"
run_check command-center-approval-signature cosign verify-blob --key "$DECISION_PUBLIC_KEY" --signature "$PHASE_DIR/command-center-approval.sig" "$PHASE_DIR/command-center-approval.json" || failed=1
run_check command-center-contract python3 - "$PHASE_DIR/approved-cutover-plan.json" "$PHASE_DIR/contact-matrix-attestation.json" "$PHASE_DIR/command-center-approval.json" "$PHASE_DIR/command-center-readiness.json" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" <<'PY' || failed=1
import datetime as dt,hashlib,json,pathlib,sys
plan_path,contacts_path,approval_path,out,reference,rc_id,commit=sys.argv[1:]
def sha(path): return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
plan=json.loads(pathlib.Path(plan_path).read_text()); contacts=json.loads(pathlib.Path(contacts_path).read_text()); approval=json.loads(pathlib.Path(approval_path).read_text())
errors=[]
if plan.get('releaseReference')!=reference or plan.get('releaseCandidateId')!=rc_id: errors.append('cutover plan identity mismatch')
required_roles={'releaseCommander','applicationOwner','databaseOwner','kafkaOwner','securityOwner','operationsOwner','businessValidationOwner','rollbackAuthority'}
roles=set(contacts.get('roles',{})); missing=sorted(required_roles-roles)
if missing: errors.append('missing command-center roles: '+', '.join(missing))
for role in required_roles & roles:
    item=contacts['roles'][role]
    if not item.get('primary') or not item.get('escalation'): errors.append(f'incomplete contact role: {role}')
steps=plan.get('steps',[])
if not steps: errors.append('cutover plan has no steps')
for i,step in enumerate(steps):
    for key in ('id','ownerRole','passCriteria','abortCriteria','rollbackCommand'):
        if not step.get(key): errors.append(f'cutover step {i} missing {key}')
approvers={x for x in approval.get('approvedBy',[]) if x}
if approval.get('decision')!='APPROVE' or len(approvers)<2: errors.append('command-center approval requires APPROVE and two approvers')
if approval.get('releaseReference')!=reference or approval.get('releaseCandidateId')!=rc_id or approval.get('gitCommit')!=commit: errors.append('command-center approval identity mismatch')
if approval.get('cutoverPlanSha256')!=sha(plan_path) or approval.get('contactMatrixSha256')!=sha(contacts_path): errors.append('command-center approval evidence hash mismatch')
try:
    approved=dt.datetime.fromisoformat(approval.get('approvedAt','').replace('Z','+00:00')); age=(dt.datetime.now(dt.timezone.utc)-approved).total_seconds()
    if age < 0 or age > 86400: errors.append('command-center approval must be issued within 24 hours')
except Exception: errors.append('command-center approvedAt invalid')
report={'schemaVersion':1,'status':'PASS' if not errors else 'FAIL','releaseReference':reference,'releaseCandidateId':rc_id,'roles':sorted(roles),'stepCount':len(steps),'approverCount':len(approvers),'errors':errors}
pathlib.Path(out).write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
raise SystemExit(0 if not errors else 2)
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
