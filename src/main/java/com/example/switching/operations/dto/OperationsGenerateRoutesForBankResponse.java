package com.example.switching.operations.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsGenerateRoutesForBankResponse(
        String status,
        LocalDateTime processedAt,
        String bankCode,
        String messageType,
        String mode,
        Integer totalCandidates,
        Integer createdCount,
        Integer skippedCount,
        List<OperationsGenerateRouteItemResponse> items
) {
}