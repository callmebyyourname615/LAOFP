package com.example.switching.iso.dto;

public record Pacs002ParseResult(
        String messageId,
        String originalMessageId,
        String originalEndToEndId,
        String originalTransactionId,
        String transactionStatus,
        String reasonCode,
        String reasonMessage
) {
    public boolean accepted() {
        return "ACSC".equalsIgnoreCase(transactionStatus)
                || "ACCP".equalsIgnoreCase(transactionStatus)
                || "ACTC".equalsIgnoreCase(transactionStatus);
    }

    public boolean rejected() {
        return "RJCT".equalsIgnoreCase(transactionStatus);
    }
}