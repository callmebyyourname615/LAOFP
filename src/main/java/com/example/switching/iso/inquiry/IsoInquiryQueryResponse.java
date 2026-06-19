package com.example.switching.iso.inquiry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class IsoInquiryQueryResponse {

    private Long id;
    private String inquiryRef;
    private String channelId;
    private String messageId;
    private String instructionId;
    private String endToEndId;

    private String sourceBank;
    private String destinationBank;

    private String debtorAccount;
    private String creditorAccount;

    private BigDecimal amount;
    private String currency;
    private String reference;

    private String status;
    private Boolean accountFound;
    private Boolean bankAvailable;
    private Boolean eligibleForTransfer;

    private String failureCode;
    private String failureMessage;

    private LocalDateTime expiresAt;
    private String usedByTransferRef;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
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

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public void setCreditorAccount(String creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUsedByTransferRef() {
        return usedByTransferRef;
    }

    public void setUsedByTransferRef(String usedByTransferRef) {
        this.usedByTransferRef = usedByTransferRef;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}