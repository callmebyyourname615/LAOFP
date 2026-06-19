package com.example.switching.qr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrCodeResponse(
    String qrId,
    String merchantId,
    String pspId,
    String qrType,
    String payload,
    BigDecimal amount,
    String currency,
    String txnRef,
    LocalDateTime expiresAt,
    LocalDateTime createdAt
) {}
