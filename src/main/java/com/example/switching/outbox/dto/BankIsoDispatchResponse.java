package com.example.switching.outbox.dto;

public record BankIsoDispatchResponse(
        boolean success,
        String responseCode,
        String responseMessage,
        String externalReference,
        String pacs002Xml,
        String isoStatusCode
) {
}