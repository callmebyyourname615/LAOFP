package com.example.switching.audit.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "actor")
    private String actor;

    @Column(name = "payload", columnDefinition = "longtext")
    private String payload;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "entry_hash", length = 64, unique = true)
    private String entryHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public String getPreviousHash() { return previousHash; }

    public String getEntryHash() { return entryHash; }

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

    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public void setEntryHash(String entryHash) { this.entryHash = entryHash; }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}