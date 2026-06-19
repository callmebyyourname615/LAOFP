package com.example.switching.qr.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrDecodeResponse(
    String qrId,
    String merchantId,
    BigDecimal amount,
    String currency,
    String qrType,
    boolean valid,
    String expiryStatus,     // VALID | EXPIRED | USED | NO_EXPIRY
    LocalDateTime expiresAt
) {}
