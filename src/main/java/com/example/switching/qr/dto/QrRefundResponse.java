package com.example.switching.qr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrRefundResponse(
    String refundTxnId,
    String originalTxnId,
    BigDecimal amount,
    String status,
    LocalDateTime initiatedAt
) {}
