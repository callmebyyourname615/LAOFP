package com.example.switching.outbox.dto;

public record StatusEnquiryResult(
        Status status,
        String externalReference,
        String responseCode,
        String responseMessage
) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        NOT_FOUND,
        PROCESSING,
        UNKNOWN
    }

    public static StatusEnquiryResult accepted(String externalReference, String message) {
        return new StatusEnquiryResult(Status.ACCEPTED, externalReference, "00", message);
    }

    public static StatusEnquiryResult rejected(String externalReference, String code, String message) {
        return new StatusEnquiryResult(Status.REJECTED, externalReference, code, message);
    }

    public static StatusEnquiryResult unknown(String code, String message) {
        return new StatusEnquiryResult(Status.UNKNOWN, null, code, message);
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    public boolean rejectedOrNotFound() {
        return status == Status.REJECTED || status == Status.NOT_FOUND;
    }
}
