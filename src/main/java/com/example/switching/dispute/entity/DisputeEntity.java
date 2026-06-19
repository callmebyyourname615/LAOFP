package com.example.switching.dispute.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "disputes")
public class DisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dispute_id")
    private Long disputeId;

    @Column(name = "txn_ref",           nullable = false, length = 200)
    private String txnRef;

    @Column(name = "raising_psp_id",    nullable = false, length = 32)
    private String raisingPspId;

    @Column(name = "responding_psp_id", nullable = false, length = 32)
    private String respondingPspId;

    @Column(name = "dispute_type",      nullable = false, length = 30)
    private String disputeType;

    @Column(name = "status",            nullable = false, length = 30)
    private String status;

    @Column(name = "raised_at",         nullable = false)
    private LocalDateTime raisedAt;

    @Column(name = "sla_deadline",      nullable = false)
    private LocalDateTime slaDeadline;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "evidence",          nullable = false, columnDefinition = "TEXT")
    private String evidence = "[]";

    @Column(name = "resolution_note",   columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "auto_ruled",        nullable = false)
    private boolean autoRuled;

    @Column(name = "created_at",        nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at",        nullable = false)
    private LocalDateTime updatedAt;

    // ── getters ───────────────────────────────────────────────────────────────

    public Long          getDisputeId()       { return disputeId; }
    public String        getTxnRef()          { return txnRef; }
    public String        getRaisingPspId()    { return raisingPspId; }
    public String        getRespondingPspId() { return respondingPspId; }
    public String        getDisputeType()     { return disputeType; }
    public String        getStatus()          { return status; }
    public LocalDateTime getRaisedAt()        { return raisedAt; }
    public LocalDateTime getSlaDeadline()     { return slaDeadline; }
    public LocalDateTime getResolvedAt()      { return resolvedAt; }
    public String        getEvidence()        { return evidence; }
    public String        getResolutionNote()  { return resolutionNote; }
    public boolean       isAutoRuled()        { return autoRuled; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public LocalDateTime getUpdatedAt()       { return updatedAt; }

    // ── setters ───────────────────────────────────────────────────────────────

    public void setDisputeId(Long v)           { this.disputeId = v; }
    public void setTxnRef(String v)            { this.txnRef = v; }
    public void setRaisingPspId(String v)      { this.raisingPspId = v; }
    public void setRespondingPspId(String v)   { this.respondingPspId = v; }
    public void setDisputeType(String v)       { this.disputeType = v; }
    public void setStatus(String v)            { this.status = v; }
    public void setRaisedAt(LocalDateTime v)   { this.raisedAt = v; }
    public void setSlaDeadline(LocalDateTime v){ this.slaDeadline = v; }
    public void setResolvedAt(LocalDateTime v) { this.resolvedAt = v; }
    public void setEvidence(String v)          { this.evidence = v; }
    public void setResolutionNote(String v)    { this.resolutionNote = v; }
    public void setAutoRuled(boolean v)        { this.autoRuled = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }
}
