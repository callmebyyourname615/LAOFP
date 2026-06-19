package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsBankStatusListResponse(
        String status,
        LocalDateTime checkedAt,
        Integer totalBanks,
        List<OperationsBankStatusItemResponse> items
) {
}