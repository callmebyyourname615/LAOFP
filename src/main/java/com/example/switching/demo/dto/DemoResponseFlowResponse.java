package com.example.switching.demo.dto;

public class DemoResponseFlowResponse {

    private String transferRef;
    private String sourceBank;
    private String destinationBank;
    private String messageType;
    private String responsePayload;
    private String switchingStatus;
    private String bankAStatus;
    private String transferStatus;

    public DemoResponseFlowResponse() {
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
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

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getSwitchingStatus() {
        return switchingStatus;
    }

    public void setSwitchingStatus(String switchingStatus) {
        this.switchingStatus = switchingStatus;
    }

    public String getBankAStatus() {
        return bankAStatus;
    }

    public void setBankAStatus(String bankAStatus) {
        this.bankAStatus = bankAStatus;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(String transferStatus) {
        this.transferStatus = transferStatus;
    }
}