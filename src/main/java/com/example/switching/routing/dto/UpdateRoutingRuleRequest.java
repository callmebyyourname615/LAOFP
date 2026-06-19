package com.example.switching.routing.dto;

/**
 * Used for PATCH /api/routing-rules/{routeCode}
 * All fields optional — only non-null fields are updated.
 */
public class UpdateRoutingRuleRequest {

    private String connectorName;
    private Integer priority;
    private Boolean enabled;

    public String getConnectorName() { return connectorName; }
    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
