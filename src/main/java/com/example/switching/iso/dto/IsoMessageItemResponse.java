package com.example.switching.iso.dto;

import java.time.LocalDateTime;

public class IsoMessageItemResponse {

    private Long id;

    private String correlationRef;
    private String inquiryRef;
    private String transferRef;

    private String endToEndId;
    private String messageId;

    private String messageType;
    private String direction;

    private String securityStatus;
    private String validationStatus;

    private String errorCode;
    private String errorMessage;

    private LocalDateTime createdAt;

    public IsoMessageItemResponse() {
    }

    public IsoMessageItemResponse(Long id,
                                  String correlationRef,
                                  String inquiryRef,
                                  String transferRef,
                                  String endToEndId,
                                  String messageId,
                                  String messageType,
                                  String direction,
                                  String securityStatus,
                                  String validationStatus,
                                  String errorCode,
                                  String errorMessage,
                                  LocalDateTime createdAt) {
        this.id = id;
        this.correlationRef = correlationRef;
        this.inquiryRef = inquiryRef;
        this.transferRef = transferRef;
        this.endToEndId = endToEndId;
        this.messageId = messageId;
        this.messageType = messageType;
        this.direction = direction;
        this.securityStatus = securityStatus;
        this.validationStatus = validationStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
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