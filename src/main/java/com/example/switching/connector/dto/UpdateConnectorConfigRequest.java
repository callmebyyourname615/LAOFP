package com.example.switching.connector.dto;

/**
 * Used for PATCH /api/connector-configs/{connectorName}
 * All fields optional — only non-null fields are updated.
 */
public class UpdateConnectorConfigRequest {

    private String endpointUrl;
    private Integer timeoutMs;
    private Boolean enabled;
    private Boolean forceReject;
    private String rejectReasonCode;
    private String rejectReasonMessage;

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getForceReject() { return forceReject; }
    public void setForceReject(Boolean forceReject) { this.forceReject = forceReject; }

    public String getRejectReasonCode() { return rejectReasonCode; }
    public void setRejectReasonCode(String rejectReasonCode) { this.rejectReasonCode = rejectReasonCode; }

    public String getRejectReasonMessage() { return rejectReasonMessage; }
    public void setRejectReasonMessage(String rejectReasonMessage) { this.rejectReasonMessage = rejectReasonMessage; }
}
