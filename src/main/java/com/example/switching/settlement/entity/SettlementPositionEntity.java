package com.example.switching.settlement.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlement_positions")
public class SettlementPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "debit_amount", nullable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    /** Computed by DB: credit_amount - debit_amount. Inserted as null, read-only after. */
    @Column(name = "net_position", insertable = false, updatable = false)
    private BigDecimal netPosition;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()                            { return id; }
    public Long getCycleId()                       { return cycleId; }
    public void setCycleId(Long v)                 { this.cycleId = v; }
    public String getBankCode()                    { return bankCode; }
    public void setBankCode(String v)              { this.bankCode = v; }
    public String getCurrency()                    { return currency; }
    public void setCurrency(String v)              { this.currency = v; }
    public BigDecimal getDebitAmount()             { return debitAmount; }
    public void setDebitAmount(BigDecimal v)       { this.debitAmount = v; }
    public BigDecimal getCreditAmount()            { return creditAmount; }
    public void setCreditAmount(BigDecimal v)      { this.creditAmount = v; }
    public BigDecimal getNetPosition()             { return netPosition; }
    public int getTransactionCount()               { return transactionCount; }
    public void setTransactionCount(int v)         { this.transactionCount = v; }
    public String getStatus()                      { return status; }
    public void setStatus(String v)                { this.status = v; }
    public LocalDateTime getSettledAt()            { return settledAt; }
    public void setSettledAt(LocalDateTime v)      { this.settledAt = v; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)      { this.updatedAt = v; }
}
