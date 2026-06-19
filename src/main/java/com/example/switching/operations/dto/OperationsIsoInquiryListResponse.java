package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsIsoInquiryListResponse(
        String status,
        LocalDateTime checkedAt,
        Long totalItems,
        Integer returnedItems,
        Integer limit,
        Integer offset,
        List<OperationsIsoInquiryItemResponse> items
) {
}
