#!/usr/bin/env bash
set -euo pipefail
DR_NAMESPACE="${DR_NAMESPACE:-switching}"
require_dr_confirmation() {
  [[ "${DR_ENVIRONMENT:-}" == "uat" || "${DR_ENVIRONMENT:-}" == "dr" ]] || { echo 'DR_ENVIRONMENT must be uat or dr' >&2; exit 2; }
  [[ "${DR_CONFIRMATION:-}" == "I_UNDERSTAND_THIS_IS_DESTRUCTIVE" ]] || { echo 'DR_CONFIRMATION missing' >&2; exit 2; }
  : "${EVIDENCE_DIR:?EVIDENCE_DIR is required}"
  mkdir -p "$EVIDENCE_DIR"
  command -v kubectl >/dev/null || { echo 'kubectl is required' >&2; exit 2; }
}
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
record() { printf '%s\t%s\t%s\n' "$(now)" "$1" "$2" | tee -a "$EVIDENCE_DIR/timeline.tsv"; }
wait_deployment() { kubectl -n "$DR_NAMESPACE" rollout status deployment/switching-api --timeout="${DR_TIMEOUT:-600s}"; }
