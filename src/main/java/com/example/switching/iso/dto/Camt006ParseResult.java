package com.example.switching.iso.dto;

public record Camt006ParseResult(
        String messageId,
        String transactionId,
        String endToEndId,
        String statusCode,
        String reasonCode,
        String errorCode,
        String errorDescription
) {
    public boolean accepted() {
        return "ACSC".equalsIgnoreCase(reasonCode)
                || "ACCP".equalsIgnoreCase(reasonCode)
                || "ACTC".equalsIgnoreCase(reasonCode)
                || "ACSP".equalsIgnoreCase(reasonCode);
    }

    public boolean rejected() {
        return "RJCT".equalsIgnoreCase(reasonCode);
    }

    public boolean notFound() {
        return "PE01".equalsIgnoreCase(errorCode);
    }
}
