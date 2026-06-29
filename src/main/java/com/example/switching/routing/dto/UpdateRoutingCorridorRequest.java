package com.example.switching.routing.dto;

public class UpdateRoutingCorridorRequest {

    private String sourceBank;
    private String destinationBank;
    private String messageType;
    private Boolean enabled;
    private String reason;

    public String getSourceBank() { return sourceBank; }
    public void setSourceBank(String sourceBank) { this.sourceBank = sourceBank; }

    public String getDestinationBank() { return destinationBank; }
    public void setDestinationBank(String destinationBank) { this.destinationBank = destinationBank; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
