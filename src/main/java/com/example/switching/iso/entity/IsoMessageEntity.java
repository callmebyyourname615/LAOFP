package com.example.switching.iso.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.switching.iso.enums.IsoMessageDirection;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.iso.enums.IsoSecurityStatus;
import com.example.switching.iso.enums.IsoValidationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "iso_messages")
public class IsoMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String correlationRef;
    private String inquiryRef;

    @Column(name = "transaction_ref")
    private String transferRef;

    private String endToEndId;
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsoMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsoMessageDirection direction;

    @Transient   // moved to iso_message_payloads table
    private String plainPayload;

    @Transient   // moved to iso_message_payloads table
    private String encryptedPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsoSecurityStatus securityStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsoValidationStatus validationStatus;

    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    private LocalDateTime createdAt;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (businessDate == null) {
            businessDate = LocalDate.now();
        }
        if (securityStatus == null) {
            securityStatus = IsoSecurityStatus.PLAIN;
        }
        if (validationStatus == null) {
            validationStatus = IsoValidationStatus.NOT_VALIDATED;
        }
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

    public IsoMessageType getMessageType() {
        return messageType;
    }

    public IsoMessageDirection getDirection() {
        return direction;
    }

    public String getPlainPayload() {
        return plainPayload;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public IsoSecurityStatus getSecurityStatus() {
        return securityStatus;
    }

    public IsoValidationStatus getValidationStatus() {
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

    public void setMessageType(IsoMessageType messageType) {
        this.messageType = messageType;
    }

    public void setDirection(IsoMessageDirection direction) {
        this.direction = direction;
    }

    public void setPlainPayload(String plainPayload) {
        this.plainPayload = plainPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public void setSecurityStatus(IsoSecurityStatus securityStatus) {
        this.securityStatus = securityStatus;
    }

    public void setValidationStatus(IsoValidationStatus validationStatus) {
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

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }
}