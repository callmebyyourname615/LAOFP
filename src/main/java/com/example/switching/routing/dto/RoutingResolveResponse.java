package com.example.switching.routing.dto;

public class RoutingResolveResponse {

    private String sourceBank;
    private String destinationBank;
    private String messageType;
    private String routeCode;
    private String connectorName;
    private Integer priority;
    private Boolean enabled;
    private String sourceParticipantStatus;
    private String destinationParticipantStatus;

    public RoutingResolveResponse() {
    }

    public String getSourceBank() {
        return sourceBank;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public Integer getPriority() {
        return priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getSourceParticipantStatus() {
        return sourceParticipantStatus;
    }

    public String getDestinationParticipantStatus() {
        return destinationParticipantStatus;
    }

    public void setSourceBank(String sourceBank) {
        this.sourceBank = sourceBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setSourceParticipantStatus(String sourceParticipantStatus) {
        this.sourceParticipantStatus = sourceParticipantStatus;
    }

    public void setDestinationParticipantStatus(String destinationParticipantStatus) {
        this.destinationParticipantStatus = destinationParticipantStatus;
    }
}