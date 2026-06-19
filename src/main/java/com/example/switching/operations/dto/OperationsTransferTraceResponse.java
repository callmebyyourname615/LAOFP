package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsTransferTraceResponse(
        String status,
        LocalDateTime checkedAt,
        String transferRef,
        String inquiryRef,
        String currentStatus,
        List<String> warnings,
        OperationsTransferTraceSummaryResponse summary,
        OperationsTransferTraceTransferResponse transfer,
        OperationsTransferTraceInquiryResponse inquiry,
        List<OperationsTransferTraceOutboxItemResponse> outboxEvents,
        List<OperationsTransferTraceIsoMessageItemResponse> isoMessages,
        List<OperationsTransferTraceAuditItemResponse> auditEvents,
        List<OperationsTransferTraceTimelineItemResponse> timeline
) {
}
