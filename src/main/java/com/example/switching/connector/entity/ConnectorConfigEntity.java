package com.example.switching.connector.entity;

import java.time.LocalDateTime;

import com.example.switching.connector.enums.ConnectorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "connector_configs")
public class ConnectorConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connector_name", nullable = false, unique = true, length = 128)
    private String connectorName;

    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 32)
    private ConnectorType connectorType;

    @Column(name = "endpoint_url", length = 512)
    private String endpointUrl;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "force_reject", nullable = false)
    private Boolean forceReject;

    @Column(name = "reject_reason_code", length = 32)
    private String rejectReasonCode;

    @Column(name = "reject_reason_message", length = 512)
    private String rejectReasonMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ConnectorConfigEntity() {
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.connectorType == null) {
            this.connectorType = ConnectorType.MOCK;
        }

        if (this.timeoutMs == null) {
            this.timeoutMs = 5000;
        }

        if (this.enabled == null) {
            this.enabled = true;
        }

        if (this.forceReject == null) {
            this.forceReject = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean forceReject() {
        return Boolean.TRUE.equals(forceReject);
    }

    public Long getId() {
        return id;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getForceReject() {
        return forceReject;
    }

    public String getRejectReasonCode() {
        return rejectReasonCode;
    }

    public String getRejectReasonMessage() {
        return rejectReasonMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setForceReject(Boolean forceReject) {
        this.forceReject = forceReject;
    }

    public void setRejectReasonCode(String rejectReasonCode) {
        this.rejectReasonCode = rejectReasonCode;
    }

    public void setRejectReasonMessage(String rejectReasonMessage) {
        this.rejectReasonMessage = rejectReasonMessage;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}