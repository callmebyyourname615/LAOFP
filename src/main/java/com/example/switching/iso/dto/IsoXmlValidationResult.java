package com.example.switching.iso.dto;

public class IsoXmlValidationResult {

    private boolean valid;
    private String errorCode;
    private String errorMessage;
    private String detectedMessageType;
    private String messageId;
    private String endToEndId;
    private String transactionStatus;

    public IsoXmlValidationResult() {
    }

    public static IsoXmlValidationResult valid(
            String detectedMessageType,
            String messageId,
            String endToEndId,
            String transactionStatus
    ) {
        IsoXmlValidationResult result = new IsoXmlValidationResult();
        result.setValid(true);
        result.setDetectedMessageType(detectedMessageType);
        result.setMessageId(messageId);
        result.setEndToEndId(endToEndId);
        result.setTransactionStatus(transactionStatus);
        return result;
    }

    public static IsoXmlValidationResult invalid(
            String errorCode,
            String errorMessage,
            String detectedMessageType
    ) {
        IsoXmlValidationResult result = new IsoXmlValidationResult();
        result.setValid(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setDetectedMessageType(detectedMessageType);
        return result;
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getDetectedMessageType() {
        return detectedMessageType;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setDetectedMessageType(String detectedMessageType) {
        this.detectedMessageType = detectedMessageType;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public void setTransactionStatus(String transactionStatus) {
        this.transactionStatus = transactionStatus;
    }
}