package com.example.switching.settlement.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlement_cycles")
public class SettlementCycleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_ref", nullable = false, unique = true, length = 40)
    private String cycleRef;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "cycle_number", nullable = false)
    private Short cycleNumber;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()                            { return id; }
    public String getCycleRef()                    { return cycleRef; }
    public void setCycleRef(String v)              { this.cycleRef = v; }
    public LocalDate getSettlementDate()           { return settlementDate; }
    public void setSettlementDate(LocalDate v)     { this.settlementDate = v; }
    public Short getCycleNumber()                  { return cycleNumber; }
    public void setCycleNumber(Short v)            { this.cycleNumber = v; }
    public String getStatus()                      { return status; }
    public void setStatus(String v)                { this.status = v; }
    public LocalDateTime getOpenedAt()             { return openedAt; }
    public void setOpenedAt(LocalDateTime v)       { this.openedAt = v; }
    public LocalDateTime getClosedAt()             { return closedAt; }
    public void setClosedAt(LocalDateTime v)       { this.closedAt = v; }
    public LocalDateTime getSettledAt()            { return settledAt; }
    public void setSettledAt(LocalDateTime v)      { this.settledAt = v; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)      { this.updatedAt = v; }
}
