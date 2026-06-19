package com.example.switching.routing.dto;

import java.time.LocalDateTime;

import com.example.switching.routing.entity.RoutingRuleEntity;

public class RoutingRuleResponse {

    private Long id;
    private String routeCode;
    private String sourceBank;
    private String destinationBank;
    private String messageType;
    private String connectorName;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RoutingRuleResponse() {
    }

    public static RoutingRuleResponse from(RoutingRuleEntity entity) {
        RoutingRuleResponse response = new RoutingRuleResponse();

        response.setId(entity.getId());
        response.setRouteCode(entity.getRouteCode());
        response.setSourceBank(entity.getSourceBank());
        response.setDestinationBank(entity.getDestinationBank());
        response.setMessageType(entity.getMessageType() == null ? null : entity.getMessageType().name());
        response.setConnectorName(entity.getConnectorName());
        response.setPriority(entity.getPriority());
        response.setEnabled(entity.getEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        return response;
    }

    public Long getId() {
        return id;
    }

    public String getRouteCode() {
        return routeCode;
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

    public String getConnectorName() {
        return connectorName;
    }

    public Integer getPriority() {
        return priority;
    }

    public Boolean getEnabled() {
        return enabled;
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

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
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

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}