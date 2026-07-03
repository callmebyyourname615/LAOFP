package com.example.switching.dispute.dto;

public record DrsDisputeDetailResponse(
        DrsDisputeListItemResponse dispute,
        DrsTransferSnapshotResponse transfer,
        DrsRefundSnapshotResponse refund
) {}
