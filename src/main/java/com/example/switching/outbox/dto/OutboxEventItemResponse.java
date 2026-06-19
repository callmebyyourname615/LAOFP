package com.example.switching.outbox.dto;

import java.time.LocalDateTime;

public class OutboxEventItemResponse {

    private Long outboxEventId;
    private String transferRef;
    private String messageType;
    private String status;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OutboxEventItemResponse() {
    }

    public OutboxEventItemResponse(Long outboxEventId,
                                   String transferRef,
                                   String messageType,
                                   String status,
                                   Integer retryCount,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
        this.outboxEventId = outboxEventId;
        this.transferRef = transferRef;
        this.messageType = messageType;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}