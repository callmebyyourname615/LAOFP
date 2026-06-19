package com.example.switching.transfer.dto;

import java.math.BigDecimal;
import java.util.List;

public class TransferTraceResponse {

    private String transferRef;
    private String currentStatus;

    private String sourceBank;
    private String destinationBank;
    private String debtorAccount;
    private String creditorAccount;
    private BigDecimal amount;
    private String currency;

    private String inquiryRef;
    private String inquiryStatus;
    private Boolean inquiryAccountFound;
    private Boolean inquiryBankAvailable;
    private Boolean inquiryEligibleForTransfer;
    private String destinationAccountName;

    private String externalReference;
    private String reference;
    private String errorCode;
    private String errorMessage;

    private List<TransferTraceHistoryItemResponse> transferHistory;
    private List<TransferTraceOutboxItemResponse> outboxEvents;
    private List<TransferTraceAuditItemResponse> auditEvents;
    private List<TransferTraceIsoMessageItemResponse> isoMessages;
    private List<TransferTraceTimelineItemResponse> timeline;

    public TransferTraceResponse() {
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getSourceBank() {
        return sourceBank;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public String getInquiryStatus() {
        return inquiryStatus;
    }

    public Boolean getInquiryAccountFound() {
        return inquiryAccountFound;
    }

    public Boolean getInquiryBankAvailable() {
        return inquiryBankAvailable;
    }

    public Boolean getInquiryEligibleForTransfer() {
        return inquiryEligibleForTransfer;
    }

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getReference() {
        return reference;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<TransferTraceHistoryItemResponse> getTransferHistory() {
        return transferHistory;
    }

    public List<TransferTraceOutboxItemResponse> getOutboxEvents() {
        return outboxEvents;
    }

    public List<TransferTraceAuditItemResponse> getAuditEvents() {
        return auditEvents;
    }

    public List<TransferTraceIsoMessageItemResponse> getIsoMessages() {
        return isoMessages;
    }

    public List<TransferTraceTimelineItemResponse> getTimeline() {
        return timeline;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public void setSourceBank(String sourceBank) {
        this.sourceBank = sourceBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
    }

    public void setCreditorAccount(String creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public void setInquiryStatus(String inquiryStatus) {
        this.inquiryStatus = inquiryStatus;
    }

    public void setInquiryAccountFound(Boolean inquiryAccountFound) {
        this.inquiryAccountFound = inquiryAccountFound;
    }

    public void setInquiryBankAvailable(Boolean inquiryBankAvailable) {
        this.inquiryBankAvailable = inquiryBankAvailable;
    }

    public void setInquiryEligibleForTransfer(Boolean inquiryEligibleForTransfer) {
        this.inquiryEligibleForTransfer = inquiryEligibleForTransfer;
    }

    public void setDestinationAccountName(String destinationAccountName) {
        this.destinationAccountName = destinationAccountName;
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

    public void setTransferHistory(List<TransferTraceHistoryItemResponse> transferHistory) {
        this.transferHistory = transferHistory;
    }

    public void setOutboxEvents(List<TransferTraceOutboxItemResponse> outboxEvents) {
        this.outboxEvents = outboxEvents;
    }

    public void setAuditEvents(List<TransferTraceAuditItemResponse> auditEvents) {
        this.auditEvents = auditEvents;
    }

    public void setIsoMessages(List<TransferTraceIsoMessageItemResponse> isoMessages) {
        this.isoMessages = isoMessages;
    }

    public void setTimeline(List<TransferTraceTimelineItemResponse> timeline) {
        this.timeline = timeline;
    }
}