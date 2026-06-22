package com.example.switching.rtp.service;

import java.math.BigDecimal;

public interface RtpSettlementGateway {
    SettlementSubmission submit(SettlementCommand command);

    record SettlementCommand(
            String sourceParticipant,
            String destinationParticipant,
            String payerAccount,
            String payeeAccount,
            BigDecimal amount,
            String currency,
            String inquiryRef,
            String idempotencyKey,
            String reference) {}

    record SettlementSubmission(String transactionReference, String status, String message) {}
}
