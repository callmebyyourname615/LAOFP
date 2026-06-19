package com.example.switching.transfer.dto;

import java.time.LocalDateTime;

public class TransferTraceTimelineItemResponse {

    private LocalDateTime timestamp;
    private String eventType;
    private String source;
    private String status;
    private String messageType;
    private String direction;
    private String referenceId;
    private String title;
    private String description;

    public TransferTraceTimelineItemResponse() {
    }

    public TransferTraceTimelineItemResponse(
            LocalDateTime timestamp,
            String eventType,
            String source,
            String status,
            String messageType,
            String direction,
            String referenceId,
            String title,
            String description
    ) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.source = source;
        this.status = status;
        this.messageType = messageType;
        this.direction = direction;
        this.referenceId = referenceId;
        this.title = title;
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}