package com.example.switching.billpayment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "billers")
public class BillerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biller_id")
    private Long billerId;

    @Column(name = "biller_code", nullable = false, length = 50)
    private String billerCode;

    @Column(name = "biller_name", nullable = false, length = 200)
    private String billerName;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 30;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── getters ───────────────────────────────────────────────────────────────

    public Long getBillerId()          { return billerId; }
    public String getBillerCode()      { return billerCode; }
    public String getBillerName()      { return billerName; }
    public String getCategory()        { return category; }
    public String getApiUrl()          { return apiUrl; }
    public String getApiKeyHash()      { return apiKeyHash; }
    public int getTimeoutSeconds()     { return timeoutSeconds; }
    public String getStatus()          { return status; }
    public LocalDateTime getCreatedAt(){ return createdAt; }

    // ── setters ───────────────────────────────────────────────────────────────

    public void setBillerId(Long v)          { this.billerId = v; }
    public void setBillerCode(String v)      { this.billerCode = v; }
    public void setBillerName(String v)      { this.billerName = v; }
    public void setCategory(String v)        { this.category = v; }
    public void setApiUrl(String v)          { this.apiUrl = v; }
    public void setApiKeyHash(String v)      { this.apiKeyHash = v; }
    public void setTimeoutSeconds(int v)     { this.timeoutSeconds = v; }
    public void setStatus(String v)          { this.status = v; }
    public void setCreatedAt(LocalDateTime v){ this.createdAt = v; }
}
