#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"; source scripts/enterprise/common.sh
enterprise_require_identity; enterprise_phase_begin 57B "Multi-Region Disaster Recovery"; enterprise_require_environment dr; enterprise_require_phase_pass 57A
: "${MULTI_REGION_TOPOLOGY_SNAPSHOT:?required}"; : "${MULTI_REGION_FAILOVER_REPORT:?required}"; : "${MULTI_REGION_FAILBACK_REPORT:?required}"
enterprise_require_confirmation MULTI_REGION_DR_EXECUTION_CONFIRMATION I_UNDERSTAND_THIS_CERTIFIES_CROSS_REGION_FAILOVER_AND_FAILBACK
enterprise_copy_input "$MULTI_REGION_TOPOLOGY_SNAPSHOT" "$PHASE_DIR/topology-snapshot.json"; enterprise_copy_input "$MULTI_REGION_FAILOVER_REPORT" "$PHASE_DIR/failover-input.json"; enterprise_copy_input "$MULTI_REGION_FAILBACK_REPORT" "$PHASE_DIR/failback-input.json"
failed=0
enterprise_run_check input-release-identity python3 scripts/enterprise/verify_input_identity.py --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST" "$PHASE_DIR/topology-snapshot.json" "$PHASE_DIR/failover-input.json" "$PHASE_DIR/failback-input.json" || failed=1
enterprise_run_check multi-region-certification python3 scripts/enterprise/verify_multi_region_dr.py --snapshot "$PHASE_DIR/topology-snapshot.json" --failover "$PHASE_DIR/failover-input.json" --failback "$PHASE_DIR/failback-input.json" --policy multi-region-dr/topology-policy.yaml --thresholds "$ENTERPRISE_THRESHOLDS" --topology-output "$PHASE_DIR/multi-region-topology-report.json" --failover-output "$PHASE_DIR/failover-certification.json" --failback-output "$PHASE_DIR/failback-certification.json" || failed=1
enterprise_write_result "$([[ $failed -eq 0 ]] && echo '' || echo FAIL)"
