package com.example.switching.qr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPayResponse(
    String txnId,
    String qrId,
    String issuingPspId,
    String acquiringPspId,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime completedAt
) {}
