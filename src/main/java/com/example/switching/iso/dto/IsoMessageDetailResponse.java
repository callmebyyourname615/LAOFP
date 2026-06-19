package com.example.switching.iso.dto;

import java.time.LocalDateTime;

public class IsoMessageDetailResponse {

    private Long id;

    private String correlationRef;
    private String inquiryRef;
    private String transferRef;

    private String endToEndId;
    private String messageId;

    private String messageType;
    private String direction;

    private String plainPayload;
    private String encryptedPayload;

    private String securityStatus;
    private String validationStatus;

    private String errorCode;
    private String errorMessage;

    private LocalDateTime createdAt;

    public IsoMessageDetailResponse() {
    }

    public Long getId() {
        return id;
    }

    public String getCorrelationRef() {
        return correlationRef;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getPlainPayload() {
        return plainPayload;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public String getSecurityStatus() {
        return securityStatus;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCorrelationRef(String correlationRef) {
        this.correlationRef = correlationRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setPlainPayload(String plainPayload) {
        this.plainPayload = plainPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public void setSecurityStatus(String securityStatus) {
        this.securityStatus = securityStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}