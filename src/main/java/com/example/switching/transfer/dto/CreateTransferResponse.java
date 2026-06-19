package com.example.switching.transfer.dto;

public class CreateTransferResponse {

    private String transferRef;
    private String status;
    private String message;

    public CreateTransferResponse() {
    }

    public CreateTransferResponse(String transferRef, String status, String message) {
        this.transferRef = transferRef;
        this.status = status;
        this.message = message;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}