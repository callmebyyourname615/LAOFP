package com.example.switching.inquiry.dto;

import java.util.List;

public class InquiryResponse {

    private String inquiryRef;
    private String status;
    private String sourceBank;
    private String destinationBank;
    private String creditorAccount;
    private String destinationAccountName;
    private Boolean accountFound;
    private Boolean bankAvailable;
    private Boolean eligibleForTransfer;
    private List<InquiryStatusHistoryItemResponse> history;

    public String getInquiryRef() {
        return inquiryRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public List<InquiryStatusHistoryItemResponse> getHistory() {
        return history;
    }

    public void setHistory(List<InquiryStatusHistoryItemResponse> history) {
        this.history = history;
    }
}
