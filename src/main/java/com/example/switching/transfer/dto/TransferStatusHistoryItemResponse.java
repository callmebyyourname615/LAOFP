package com.example.switching.transfer.dto;

public class TransferStatusHistoryItemResponse {

    private String status;
    private String reasonCode;
    private String createdAt;

    public TransferStatusHistoryItemResponse() {
    }

    public TransferStatusHistoryItemResponse(String status, String reasonCode, String createdAt) {
        this.status = status;
        this.reasonCode = reasonCode;
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}