package com.example.switching.outbox.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent record of every auto-reversal triggered by {@link com.example.switching.outbox.service.OutboxAutoReversalService}.
 *
 * <p>reason values: MAX_RETRIES | COMPLIANCE_BLOCK | EXPIRED
 * <p>status values: INITIATED | COMPLETED | FAILED
 */
@Entity
@Table(name = "reversal_log")
public class ReversalLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reversal_id")
    private Long reversalId;

    @Column(name = "original_txn_id", nullable = false, length = 36)
    private String originalTxnId;

    @Column(name = "reversal_txn_id", length = 36)
    private String reversalTxnId;

    @Column(name = "destination_bank", nullable = false, length = 32)
    private String destinationBank;

    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_class", length = 40)
    private String failureClass;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ReversalLogEntity() {
    }

    public Long getReversalId()                { return reversalId; }
    public String getOriginalTxnId()           { return originalTxnId; }
    public void   setOriginalTxnId(String v)   { this.originalTxnId = v; }
    public String getReversalTxnId()           { return reversalTxnId; }
    public void   setReversalTxnId(String v)   { this.reversalTxnId = v; }
    public String getDestinationBank()         { return destinationBank; }
    public void   setDestinationBank(String v) { this.destinationBank = v; }
    public String getReason()                  { return reason; }
    public void   setReason(String v)          { this.reason = v; }
    public String getStatus()                  { return status; }
    public void   setStatus(String v)          { this.status = v; }
    public String getFailureClass()            { return failureClass; }
    public void   setFailureClass(String v)    { this.failureClass = v; }
    public LocalDateTime getTriggeredAt()      { return triggeredAt; }
    public void   setTriggeredAt(LocalDateTime v) { this.triggeredAt = v; }
    public LocalDateTime getCompletedAt()      { return completedAt; }
    public void   setCompletedAt(LocalDateTime v) { this.completedAt = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void   setCreatedAt(LocalDateTime v)   { this.createdAt = v; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void   setUpdatedAt(LocalDateTime v)   { this.updatedAt = v; }
}
