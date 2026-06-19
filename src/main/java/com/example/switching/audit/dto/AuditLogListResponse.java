package com.example.switching.audit.dto;

import java.util.List;

public class AuditLogListResponse {

    private int count;
    private int limit;
    private String eventType;
    private String referenceType;
    private String referenceId;
    private String actor;
    private List<AuditLogItemResponse> items;

    public AuditLogListResponse() {
    }

    public AuditLogListResponse(int count,
                                int limit,
                                String eventType,
                                String referenceType,
                                String referenceId,
                                String actor,
                                List<AuditLogItemResponse> items) {
        this.count = count;
        this.limit = limit;
        this.eventType = eventType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actor = actor;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public int getLimit() {
        return limit;
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

    public List<AuditLogItemResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setLimit(int limit) {
        this.limit = limit;
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

    public void setItems(List<AuditLogItemResponse> items) {
        this.items = items;
    }
}