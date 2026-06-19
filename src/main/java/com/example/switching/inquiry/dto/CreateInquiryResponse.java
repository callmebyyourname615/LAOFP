package com.example.switching.inquiry.dto;

public class CreateInquiryResponse {

    private String inquiryRef;
    private String status;
    private Boolean accountFound;
    private Boolean bankAvailable;
    private Boolean eligibleForTransfer;
    private String destinationAccountName;
    private String message;

    public CreateInquiryResponse() {
    }

    public CreateInquiryResponse(String inquiryRef,
                                 String status,
                                 Boolean accountFound,
                                 Boolean bankAvailable,
                                 Boolean eligibleForTransfer,
                                 String destinationAccountName,
                                 String message) {
        this.inquiryRef = inquiryRef;
        this.status = status;
        this.accountFound = accountFound;
        this.bankAvailable = bankAvailable;
        this.eligibleForTransfer = eligibleForTransfer;
        this.destinationAccountName = destinationAccountName;
        this.message = message;
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

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public void setDestinationAccountName(String destinationAccountName) {
        this.destinationAccountName = destinationAccountName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
