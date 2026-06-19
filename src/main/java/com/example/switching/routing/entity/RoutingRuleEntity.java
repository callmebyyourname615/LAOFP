package com.example.switching.routing.entity;

import java.time.LocalDateTime;

import com.example.switching.iso.enums.IsoMessageType;

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
@Table(name = "routing_rules")
public class RoutingRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_code", nullable = false, unique = true, length = 128)
    private String routeCode;

    @Column(name = "source_bank", nullable = false, length = 32)
    private String sourceBank;

    @Column(name = "destination_bank", nullable = false, length = 32)
    private String destinationBank;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private IsoMessageType messageType;

    @Column(name = "connector_name", nullable = false, length = 128)
    private String connectorName;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public RoutingRuleEntity() {
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.priority == null) {
            this.priority = 1;
        }

        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public IsoMessageType getMessageType() {
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

    public void setMessageType(IsoMessageType messageType) {
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