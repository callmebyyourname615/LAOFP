package com.example.switching.iso.dto;

import java.time.LocalDateTime;

public class IsoMessageValidationActionResponse {

    private Long id;
    private String messageId;
    private String transferRef;
    private String messageType;
    private String direction;
    private String validationStatus;
    private boolean valid;
    private String detectedMessageType;
    private String detectedMessageId;
    private String detectedEndToEndId;
    private String detectedTransactionStatus;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime validatedAt;

    public IsoMessageValidationActionResponse() {
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public boolean isValid() {
        return valid;
    }

    public String getDetectedMessageType() {
        return detectedMessageType;
    }

    public String getDetectedMessageId() {
        return detectedMessageId;
    }

    public String getDetectedEndToEndId() {
        return detectedEndToEndId;
    }

    public String getDetectedTransactionStatus() {
        return detectedTransactionStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setDetectedMessageType(String detectedMessageType) {
        this.detectedMessageType = detectedMessageType;
    }

    public void setDetectedMessageId(String detectedMessageId) {
        this.detectedMessageId = detectedMessageId;
    }

    public void setDetectedEndToEndId(String detectedEndToEndId) {
        this.detectedEndToEndId = detectedEndToEndId;
    }

    public void setDetectedTransactionStatus(String detectedTransactionStatus) {
        this.detectedTransactionStatus = detectedTransactionStatus;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }
}