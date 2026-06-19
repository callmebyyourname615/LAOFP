package com.example.switching.outbox.dto;

public class OutboxManualRetryResponse {

    private Long outboxEventId;
    private String transferRef;
    private String previousStatus;
    private String newStatus;
    private Integer retryCount;
    private String message;

    public OutboxManualRetryResponse() {
    }

    public OutboxManualRetryResponse(Long outboxEventId,
                                     String transferRef,
                                     String previousStatus,
                                     String newStatus,
                                     Integer retryCount,
                                     String message) {
        this.outboxEventId = outboxEventId;
        this.transferRef = transferRef;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.retryCount = retryCount;
        this.message = message;
    }

    public Long getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(Long outboxEventId) {
        this.outboxEventId = outboxEventId;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}