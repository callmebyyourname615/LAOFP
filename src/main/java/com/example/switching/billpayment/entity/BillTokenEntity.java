package com.example.switching.billpayment.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bill_tokens")
public class BillTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "biller_id", nullable = false)
    private Long billerId;

    @Column(name = "bill_ref", nullable = false, length = 200)
    private String billRef;

    @Column(name = "bill_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal billAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    // ── getters ───────────────────────────────────────────────────────────────

    public Long getTokenId()           { return tokenId; }
    public Long getBillerId()          { return billerId; }
    public String getBillRef()         { return billRef; }
    public BigDecimal getBillAmount()  { return billAmount; }
    public LocalDate getDueDate()      { return dueDate; }
    public String getCustomerName()    { return customerName; }
    public String getDetails()         { return details; }
    public LocalDateTime getFetchedAt(){ return fetchedAt; }
    public LocalDateTime getExpiresAt(){ return expiresAt; }
    public boolean isUsed()            { return used; }

    // ── setters ───────────────────────────────────────────────────────────────

    public void setTokenId(Long v)           { this.tokenId = v; }
    public void setBillerId(Long v)          { this.billerId = v; }
    public void setBillRef(String v)         { this.billRef = v; }
    public void setBillAmount(BigDecimal v)  { this.billAmount = v; }
    public void setDueDate(LocalDate v)      { this.dueDate = v; }
    public void setCustomerName(String v)    { this.customerName = v; }
    public void setDetails(String v)         { this.details = v; }
    public void setFetchedAt(LocalDateTime v){ this.fetchedAt = v; }
    public void setExpiresAt(LocalDateTime v){ this.expiresAt = v; }
    public void setUsed(boolean v)           { this.used = v; }
}
