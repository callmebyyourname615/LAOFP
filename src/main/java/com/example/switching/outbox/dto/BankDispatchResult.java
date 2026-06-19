package com.example.switching.outbox.dto;

public class BankDispatchResult {

    private boolean success;
    private String externalReference;
    private String reference;
    private String errorCode;
    private String errorMessage;

    public BankDispatchResult() {
    }

    public BankDispatchResult(boolean success,
                              String externalReference,
                              String reference,
                              String errorCode,
                              String errorMessage) {
        this.success = success;
        this.externalReference = externalReference;
        this.reference = reference;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static BankDispatchResult success(String externalReference, String reference) {
        return new BankDispatchResult(true, externalReference, reference, null, null);
    }

    public static BankDispatchResult failed(String errorCode, String errorMessage) {
        return new BankDispatchResult(false, null, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean success() {
        return success;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String externalReference() {
        return externalReference;
    }

    public String getReference() {
        return reference;
    }

    public String reference() {
        return reference;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}