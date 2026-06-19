package com.example.switching.webhook.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_registrations")
public class WebhookRegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false, unique = true, length = 36)
    private String webhookId;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** JSON array of subscribed event type strings. */
    @Column(name = "event_types", nullable = false, columnDefinition = "TEXT")
    private String eventTypes;

    /** Self-contained envelope ciphertext. Plaintext is never persisted. */
    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String secretCiphertext;

    /** Logical Vault/KMS key reference used for operational visibility. */
    @Column(name = "secret_key_id", nullable = false, length = 200)
    private String secretKeyId;

    @Column(name = "secret_version", nullable = false)
    private int secretVersion = 1;

    /** Previous self-contained envelope retained only for the configured rotation grace period. */
    @Column(name = "previous_secret_ciphertext", columnDefinition = "TEXT")
    private String previousSecretCiphertext;

    @Column(name = "previous_secret_expires_at")
    private LocalDateTime previousSecretExpiresAt;

    /** SHA-256 hex of secret — for display / rotation comparison only. */
    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "failed_deliveries", nullable = false)
    private int failedDeliveries;

    @Column(name = "last_delivered_at")
    private LocalDateTime lastDeliveredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWebhookId() { return webhookId; }
    public void setWebhookId(String webhookId) { this.webhookId = webhookId; }
    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getEventTypes() { return eventTypes; }
    public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }
    public String getSecretCiphertext() { return secretCiphertext; }
    public void setSecretCiphertext(String secretCiphertext) { this.secretCiphertext = secretCiphertext; }
    public String getSecretKeyId() { return secretKeyId; }
    public void setSecretKeyId(String secretKeyId) { this.secretKeyId = secretKeyId; }
    public int getSecretVersion() { return secretVersion; }
    public void setSecretVersion(int secretVersion) { this.secretVersion = secretVersion; }
    public String getPreviousSecretCiphertext() { return previousSecretCiphertext; }
    public void setPreviousSecretCiphertext(String previousSecretCiphertext) {
        this.previousSecretCiphertext = previousSecretCiphertext;
    }
    public LocalDateTime getPreviousSecretExpiresAt() { return previousSecretExpiresAt; }
    public void setPreviousSecretExpiresAt(LocalDateTime previousSecretExpiresAt) {
        this.previousSecretExpiresAt = previousSecretExpiresAt;
    }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFailedDeliveries() { return failedDeliveries; }
    public void setFailedDeliveries(int failedDeliveries) { this.failedDeliveries = failedDeliveries; }
    public LocalDateTime getLastDeliveredAt() { return lastDeliveredAt; }
    public void setLastDeliveredAt(LocalDateTime lastDeliveredAt) { this.lastDeliveredAt = lastDeliveredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
