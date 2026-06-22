package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import com.example.switching.rtp.enums.RtpStatus;

public record RtpAuthorisationResponse(
        UUID requestId,
        String authorisationReference,
        RtpAuthorisationMode mode,
        BigDecimal authorisedAmount,
        String transferReference,
        RtpStatus status,
        List<RtpInstallmentView> installments) {
    public record RtpInstallmentView(int installmentNumber, java.time.Instant dueAt,
            BigDecimal amount, String status, String transactionReference) {}
}
