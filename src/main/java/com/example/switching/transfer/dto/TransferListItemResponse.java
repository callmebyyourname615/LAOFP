package com.example.switching.transfer.dto;

import java.math.BigDecimal;

public class TransferListItemResponse {

    private String transferRef;
    private String inquiryRef;
    private String status;
    private String sourceBank;
    private String debtorAccount;
    private String destinationBank;
    private String creditorAccount;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private String externalReference;
    private String errorCode;
    private String errorMessage;

    public TransferListItemResponse() {
    }

    public TransferListItemResponse(String transferRef,
                                    String inquiryRef,
                                    String status,
                                    String sourceBank,
                                    String debtorAccount,
                                    String destinationBank,
                                    String creditorAccount,
                                    BigDecimal amount,
                                    String currency,
                                    String reference,
                                    String externalReference,
                                    String errorCode,
                                    String errorMessage) {
        this.transferRef = transferRef;
        this.inquiryRef = inquiryRef;
        this.status = status;
        this.sourceBank = sourceBank;
        this.debtorAccount = debtorAccount;
        this.destinationBank = destinationBank;
        this.creditorAccount = creditorAccount;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.externalReference = externalReference;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

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

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
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

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
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
}