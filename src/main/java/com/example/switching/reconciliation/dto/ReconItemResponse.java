package com.example.switching.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconItemResponse(
        Long id,
        Long fileId,
        int lineNumber,
        String transactionRef,
        String externalRef,
        BigDecimal amount,
        String currency,
        String matchStatus,
        String mismatchReason,
        LocalDate reconciliationDate,
        LocalDateTime matchedAt
) {}
