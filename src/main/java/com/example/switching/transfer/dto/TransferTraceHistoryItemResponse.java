package com.example.switching.transfer.dto;

import java.time.LocalDateTime;

public class TransferTraceHistoryItemResponse {

    private String status;
    private String reasonCode;
    private LocalDateTime createdAt;

    public TransferTraceHistoryItemResponse() {
    }

    public TransferTraceHistoryItemResponse(String status, String reasonCode, LocalDateTime createdAt) {
        this.status = status;
        this.reasonCode = reasonCode;
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}