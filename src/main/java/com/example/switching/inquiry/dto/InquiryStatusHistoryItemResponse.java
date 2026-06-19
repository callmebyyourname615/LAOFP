package com.example.switching.inquiry.dto;

public class InquiryStatusHistoryItemResponse {

    private String status;
    private String reasonCode;
    private String createdAt;

    public InquiryStatusHistoryItemResponse() {
    }

    public InquiryStatusHistoryItemResponse(String status, String reasonCode, String createdAt) {
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
