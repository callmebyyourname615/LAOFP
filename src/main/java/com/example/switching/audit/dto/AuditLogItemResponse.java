package com.example.switching.audit.dto;

import java.time.LocalDateTime;

public class AuditLogItemResponse {

    private Long id;
    private String eventType;
    private String referenceType;
    private String referenceId;
    private String actor;
    private String payload;
    private LocalDateTime createdAt;

    public AuditLogItemResponse() {
    }

    public AuditLogItemResponse(Long id,
                                String eventType,
                                String referenceType,
                                String referenceId,
                                String actor,
                                String payload,
                                LocalDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actor = actor;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getActor() {
        return actor;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}