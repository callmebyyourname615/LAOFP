package com.example.switching.dispute.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.switching.dispute.dto.DrsActionsResponse;
import com.example.switching.dispute.dto.DrsDisputeDetailResponse;
import com.example.switching.dispute.dto.DrsDisputeListItemResponse;
import com.example.switching.dispute.dto.DrsRefundSnapshotResponse;
import com.example.switching.operations.dto.OperationActionDecisionResponse;

@Service
public class DrsActionReadinessService {

    private final DrsOperationsQueryService queryService;

    public DrsActionReadinessService(DrsOperationsQueryService queryService) {
        this.queryService = queryService;
    }

    public DrsActionsResponse actions(Long disputeId) {
        DrsDisputeDetailResponse detail = queryService.detail(disputeId);
        DrsDisputeListItemResponse dispute = detail.dispute();
        DrsRefundSnapshotResponse refund = detail.refund();

        Map<String, OperationActionDecisionResponse> actions = new LinkedHashMap<>();
        actions.put("canSubmitResolution", canSubmitResolution(dispute));
        actions.put("canApproveResolution", canApproveResolution(dispute));
        actions.put("canRejectResolution", canRejectResolution(dispute));
        actions.put("canRetryRefund", canRetryRefund(dispute, refund));
        actions.put("canUploadEvidence", canUploadEvidence(dispute));
        actions.put("canDownloadEvidenceReport", OperationActionDecisionResponse.allowed("Evidence report is available for all existing disputes"));

        return new DrsActionsResponse(
                LocalDateTime.now(),
                dispute.disputeId(),
                dispute.txnRef(),
                dispute.status(),
                dispute.proposedResolution(),
                dispute.proposedBy(),
                refund == null ? null : refund.refundId(),
                refund == null ? null : refund.status(),
                actions);
    }

    private OperationActionDecisionResponse canSubmitResolution(DrsDisputeListItemResponse dispute) {
        if ("OPEN".equals(dispute.status()) || "UNDER_REVIEW".equals(dispute.status()) || "ESCALATED".equals(dispute.status())) {
            return OperationActionDecisionResponse.allowed("Dispute can be submitted by maker for checker approval");
        }
        return OperationActionDecisionResponse.denied(
                "Dispute status is " + dispute.status() + "; submit requires OPEN, UNDER_REVIEW, or ESCALATED");
    }

    private OperationActionDecisionResponse canApproveResolution(DrsDisputeListItemResponse dispute) {
        if ("PENDING_APPROVAL".equals(dispute.status())) {
            return OperationActionDecisionResponse.allowed("Dispute is waiting for checker approval");
        }
        return OperationActionDecisionResponse.denied(
                "Dispute status is " + dispute.status() + "; approve requires PENDING_APPROVAL");
    }

    private OperationActionDecisionResponse canRejectResolution(DrsDisputeListItemResponse dispute) {
        if ("PENDING_APPROVAL".equals(dispute.status())) {
            return OperationActionDecisionResponse.allowed("Dispute is waiting for checker decision");
        }
        return OperationActionDecisionResponse.denied(
                "Dispute status is " + dispute.status() + "; reject requires PENDING_APPROVAL");
    }

    private OperationActionDecisionResponse canRetryRefund(
            DrsDisputeListItemResponse dispute,
            DrsRefundSnapshotResponse refund) {
        if (!"ESCALATED".equals(dispute.status())) {
            return OperationActionDecisionResponse.denied(
                    "Dispute status is " + dispute.status() + "; refund retry requires ESCALATED");
        }
        if (refund == null) {
            return OperationActionDecisionResponse.denied("No refund attempt exists for this dispute");
        }
        if (!"FAILED".equals(refund.status())) {
            return OperationActionDecisionResponse.denied(
                    "Latest refund status is " + refund.status() + "; retry requires FAILED");
        }
        return OperationActionDecisionResponse.allowed("Latest refund FAILED and dispute is ESCALATED");
    }

    private OperationActionDecisionResponse canUploadEvidence(DrsDisputeListItemResponse dispute) {
        if ("RESOLVED_REFUND".equals(dispute.status())
                || "RESOLVED_NO_ACTION".equals(dispute.status())
                || "CLOSED".equals(dispute.status())) {
            return OperationActionDecisionResponse.denied(
                    "Dispute status is " + dispute.status() + "; evidence upload is for active disputes");
        }
        return OperationActionDecisionResponse.allowed("Dispute is active and can accept supporting evidence");
    }
}
