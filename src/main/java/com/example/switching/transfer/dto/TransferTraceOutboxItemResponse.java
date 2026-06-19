package com.example.switching.transfer.dto;

import java.time.LocalDateTime;

public class TransferTraceOutboxItemResponse {

    private Long outboxEventId;
    private String messageType;
    private String status;
    private Integer retryCount;
    private LocalDateTime createdAt;

    public TransferTraceOutboxItemResponse() {
    }

    public TransferTraceOutboxItemResponse(Long outboxEventId,
                                           String messageType,
                                           String status,
                                           Integer retryCount,
                                           LocalDateTime createdAt) {
        this.outboxEventId = outboxEventId;
        this.messageType = messageType;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public Long getOutboxEventId() {
        return outboxEventId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setOutboxEventId(Long outboxEventId) {
        this.outboxEventId = outboxEventId;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}