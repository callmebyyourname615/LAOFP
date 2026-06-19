package com.example.switching.inquiry.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InquiryMonitorListResponse(
        String status,
        LocalDateTime checkedAt,
        long totalItems,
        int returnedItems,
        int limit,
        int offset,
        List<InquiryMonitorItemResponse> items
) {
    public static InquiryMonitorListResponse of(
            long totalItems,
            int limit,
            int offset,
            List<InquiryMonitorItemResponse> items
    ) {
        return new InquiryMonitorListResponse(
                totalItems > 0 ? "HAS_INQUIRIES" : "EMPTY",
                LocalDateTime.now(),
                totalItems,
                items == null ? 0 : items.size(),
                limit,
                offset,
                items
        );
    }
}