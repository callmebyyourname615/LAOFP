package com.example.switching.inquiry.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.switching.inquiry.enums.InquiryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inquiries")
public class InquiryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_ref", nullable = false, unique = true)
    private String inquiryRef;

    @Column(name = "client_inquiry_id")
    private String clientInquiryId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "flow_ref")
    private String flowRef;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "instruction_id")
    private String instructionId;

    @Column(name = "end_to_end_id")
    private String endToEndId;

    @Column(name = "debtor_account")
    private String debtorAccount;

    @Column(name = "used_by_transaction_ref")
    private String usedByTransactionRef;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "source_bank", nullable = false)
    private String sourceBank;

    @Column(name = "destination_bank", nullable = false)
    private String destinationBank;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(name = "destination_account_name")
    private String destinationAccountName;

    @Column(name = "amount")
    private java.math.BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "route_code")
    private String routeCode;

    @Column(name = "connector_name")
    private String connectorName;

    @Column(name = "account_found", nullable = false)
    private Boolean accountFound;

    @Column(name = "bank_available", nullable = false)
    private Boolean bankAvailable;

    @Column(name = "eligible_for_transfer", nullable = false)
    private Boolean eligibleForTransfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InquiryStatus status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "reference")
    private String reference;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate = LocalDate.now();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public String getClientInquiryId() {
        return clientInquiryId;
    }

    public void setClientInquiryId(String clientInquiryId) {
        this.clientInquiryId = clientInquiryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFlowRef() {
        return flowRef;
    }

    public void setFlowRef(String flowRef) {
        this.flowRef = flowRef;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
    }

    public String getUsedByTransactionRef() {
        return usedByTransactionRef;
    }

    public void setUsedByTransactionRef(String usedByTransactionRef) {
        this.usedByTransactionRef = usedByTransactionRef;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSourceBank() {
        return sourceBank;
    }

    public void setSourceBank(String sourceBank) {
        this.sourceBank = sourceBank;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public void setCreditorAccount(String creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public void setDestinationAccountName(String destinationAccountName) {
        this.destinationAccountName = destinationAccountName;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public Boolean getAccountFound() {
        return accountFound;
    }

    public void setAccountFound(Boolean accountFound) {
        this.accountFound = accountFound;
    }

    public Boolean getBankAvailable() {
        return bankAvailable;
    }

    public void setBankAvailable(Boolean bankAvailable) {
        this.bankAvailable = bankAvailable;
    }

    public Boolean getEligibleForTransfer() {
        return eligibleForTransfer;
    }

    public void setEligibleForTransfer(Boolean eligibleForTransfer) {
        this.eligibleForTransfer = eligibleForTransfer;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public void setStatus(InquiryStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
