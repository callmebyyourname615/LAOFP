package com.example.switching.connector.dto;

import java.time.LocalDateTime;

import com.example.switching.connector.entity.ConnectorConfigEntity;

public class ConnectorConfigResponse {

    private Long id;
    private String connectorName;
    private String bankCode;
    private String connectorType;
    private String endpointUrl;
    private Integer timeoutMs;
    private Boolean enabled;
    private Boolean forceReject;
    private String rejectReasonCode;
    private String rejectReasonMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConnectorConfigResponse() {
    }

    public static ConnectorConfigResponse from(ConnectorConfigEntity entity) {
        ConnectorConfigResponse response = new ConnectorConfigResponse();

        response.setId(entity.getId());
        response.setConnectorName(entity.getConnectorName());
        response.setBankCode(entity.getBankCode());
        response.setConnectorType(entity.getConnectorType() == null ? null : entity.getConnectorType().name());
        response.setEndpointUrl(entity.getEndpointUrl());
        response.setTimeoutMs(entity.getTimeoutMs());
        response.setEnabled(entity.getEnabled());
        response.setForceReject(entity.getForceReject());
        response.setRejectReasonCode(entity.getRejectReasonCode());
        response.setRejectReasonMessage(entity.getRejectReasonMessage());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        return response;
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

    public String getConnectorType() {
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

    public void setConnectorType(String connectorType) {
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