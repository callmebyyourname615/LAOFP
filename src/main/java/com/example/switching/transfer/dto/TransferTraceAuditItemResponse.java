package com.example.switching.transfer.dto;

import java.time.LocalDateTime;

public class TransferTraceAuditItemResponse {

    private Long auditLogId;
    private String eventType;
    private String entityType;
    private String entityRef;
    private String sourceSystem;
    private LocalDateTime createdAt;

    public TransferTraceAuditItemResponse() {
    }

    public TransferTraceAuditItemResponse(Long auditLogId,
                                          String eventType,
                                          String entityType,
                                          String entityRef,
                                          String sourceSystem,
                                          LocalDateTime createdAt) {
        this.auditLogId = auditLogId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityRef = entityRef;
        this.sourceSystem = sourceSystem;
        this.createdAt = createdAt;
    }

    public Long getAuditLogId() {
        return auditLogId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityRef() {
        return entityRef;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setAuditLogId(Long auditLogId) {
        this.auditLogId = auditLogId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setEntityRef(String entityRef) {
        this.entityRef = entityRef;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}