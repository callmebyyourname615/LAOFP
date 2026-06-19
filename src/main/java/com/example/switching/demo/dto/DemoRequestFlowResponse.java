package com.example.switching.demo.dto;

public class DemoRequestFlowResponse {

    private String transferRef;
    private String sourceBank;
    private String destinationBank;
    private String messageType;
    private String requestPayload;
    private String switchingStatus;
    private String routingResult;
    private String bankBStatus;

    public DemoRequestFlowResponse() {
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

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getSwitchingStatus() {
        return switchingStatus;
    }

    public void setSwitchingStatus(String switchingStatus) {
        this.switchingStatus = switchingStatus;
    }

    public String getRoutingResult() {
        return routingResult;
    }

    public void setRoutingResult(String routingResult) {
        this.routingResult = routingResult;
    }

    public String getBankBStatus() {
        return bankBStatus;
    }

    public void setBankBStatus(String bankBStatus) {
        this.bankBStatus = bankBStatus;
    }
}