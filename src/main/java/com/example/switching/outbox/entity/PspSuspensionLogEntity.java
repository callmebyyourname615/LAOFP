package com.example.switching.outbox.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit log for every PSP auto-suspension triggered by
 * {@link com.example.switching.outbox.service.PspAutoSuspensionService}.
 */
@Entity
@Table(name = "psp_suspension_log")
public class PspSuspensionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suspension_id")
    private Long suspensionId;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    @Column(name = "reversal_count", nullable = false)
    private int reversalCount;

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "reinstated_at")
    private LocalDateTime reinstatedAt;

    @Column(name = "reinstated_by", length = 100)
    private String reinstatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PspSuspensionLogEntity() {
    }

    public Long   getSuspensionId()            { return suspensionId; }
    public String getPspId()                   { return pspId; }
    public void   setPspId(String v)           { this.pspId = v; }
    public LocalDateTime getSuspendedAt()      { return suspendedAt; }
    public void   setSuspendedAt(LocalDateTime v) { this.suspendedAt = v; }
    public int    getReversalCount()           { return reversalCount; }
    public void   setReversalCount(int v)      { this.reversalCount = v; }
    public int    getWindowMinutes()           { return windowMinutes; }
    public void   setWindowMinutes(int v)      { this.windowMinutes = v; }
    public LocalDateTime getReinstatedAt()     { return reinstatedAt; }
    public void   setReinstatedAt(LocalDateTime v) { this.reinstatedAt = v; }
    public String getReinstatedBy()            { return reinstatedBy; }
    public void   setReinstatedBy(String v)    { this.reinstatedBy = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void   setCreatedAt(LocalDateTime v)   { this.createdAt = v; }
}
