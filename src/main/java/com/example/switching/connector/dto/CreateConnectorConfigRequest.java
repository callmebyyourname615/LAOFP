package com.example.switching.connector.dto;

public class CreateConnectorConfigRequest {

    private String connectorName;
    private String bankCode;
    private String connectorType;   // MOCK | HTTP | MQ
    private String endpointUrl;
    private Integer timeoutMs;      // default 5000
    private Boolean enabled;        // default true
    private Boolean forceReject;    // default false
    private String rejectReasonCode;
    private String rejectReasonMessage;

    public String getConnectorName() { return connectorName; }
    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

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
