package com.example.switching.routing.dto;

public class CreateRoutingRuleRequest {

    private String routeCode;
    private String sourceBank;
    private String destinationBank;
    private String messageType;      // PACS_008 | PACS_002 | PACS_028 | PACS_004
    private String connectorName;
    private Integer priority;        // default 1
    private Boolean enabled;         // default true

    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

    public String getSourceBank() { return sourceBank; }
    public void setSourceBank(String sourceBank) { this.sourceBank = sourceBank; }

    public String getDestinationBank() { return destinationBank; }
    public void setDestinationBank(String destinationBank) { this.destinationBank = destinationBank; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getConnectorName() { return connectorName; }
    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
