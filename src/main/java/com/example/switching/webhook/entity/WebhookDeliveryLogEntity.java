package com.example.switching.webhook.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_delivery_log")
public class WebhookDeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false, length = 36)
    private String webhookId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** e.g. transfer_ref — used for correlation lookups. */
    @Column(name = "event_ref", length = 80)
    private String eventRef;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** PENDING | DELIVERED | FAILED_FINAL */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId()                              { return id; }
    public String getWebhookId()                     { return webhookId; }
    public void setWebhookId(String webhookId)       { this.webhookId = webhookId; }
    public String getEventType()                     { return eventType; }
    public void setEventType(String eventType)       { this.eventType = eventType; }
    public String getEventRef()                      { return eventRef; }
    public void setEventRef(String eventRef)         { this.eventRef = eventRef; }
    public String getPayload()                       { return payload; }
    public void setPayload(String payload)           { this.payload = payload; }
    public int getAttemptCount()                     { return attemptCount; }
    public void setAttemptCount(int attemptCount)    { this.attemptCount = attemptCount; }
    public LocalDateTime getLastAttemptAt()          { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime t)    { this.lastAttemptAt = t; }
    public LocalDateTime getNextRetryAt()            { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime t)      { this.nextRetryAt = t; }
    public Integer getResponseStatus()               { return responseStatus; }
    public void setResponseStatus(Integer s)         { this.responseStatus = s; }
    public LocalDateTime getDeliveredAt()            { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime t)      { this.deliveredAt = t; }
    public String getStatus()                        { return status; }
    public void setStatus(String status)             { this.status = status; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)        { this.updatedAt = t; }
}
