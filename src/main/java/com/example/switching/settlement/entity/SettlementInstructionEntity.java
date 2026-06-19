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
@Table(name = "settlement_instructions")
public class SettlementInstructionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instruction_ref", nullable = false, unique = true, length = 80)
    private String instructionRef;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "source_type", nullable = false, length = 24)
    private String sourceType = "DNS_CYCLE";

    @Column(name = "transfer_ref", length = 64)
    private String transferRef;

    @Column(name = "debtor_psp_id", nullable = false, length = 32)
    private String debtorPspId;

    @Column(name = "creditor_psp_id", nullable = false, length = 32)
    private String creditorPspId;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "LAK";

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING_APPROVAL";

    @Column(name = "approval_note")
    private String approvalNote;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "rtgs_msg_id", length = 100)
    private String rtgsMsgId;

    @Column(name = "rtgs_request_payload")
    private String rtgsRequestPayload;

    @Column(name = "rtgs_response_payload")
    private String rtgsResponsePayload;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()                                      { return id; }
    public String getInstructionRef()                        { return instructionRef; }
    public void setInstructionRef(String v)                  { this.instructionRef = v; }
    public Long getCycleId()                                 { return cycleId; }
    public void setCycleId(Long v)                           { this.cycleId = v; }
    public String getSourceType()                            { return sourceType; }
    public void setSourceType(String v)                      { this.sourceType = v; }
    public String getTransferRef()                           { return transferRef; }
    public void setTransferRef(String v)                     { this.transferRef = v; }
    public String getDebtorPspId()                           { return debtorPspId; }
    public void setDebtorPspId(String v)                     { this.debtorPspId = v; }
    public String getCreditorPspId()                         { return creditorPspId; }
    public void setCreditorPspId(String v)                   { this.creditorPspId = v; }
    public String getCurrency()                              { return currency; }
    public void setCurrency(String v)                        { this.currency = v; }
    public BigDecimal getNetAmount()                         { return netAmount; }
    public void setNetAmount(BigDecimal v)                   { this.netAmount = v; }
    public String getStatus()                                { return status; }
    public void setStatus(String v)                          { this.status = v; }
    public String getApprovalNote()                          { return approvalNote; }
    public void setApprovalNote(String v)                    { this.approvalNote = v; }
    public String getApprovedBy()                            { return approvedBy; }
    public void setApprovedBy(String v)                      { this.approvedBy = v; }
    public LocalDateTime getApprovedAt()                     { return approvedAt; }
    public void setApprovedAt(LocalDateTime v)               { this.approvedAt = v; }
    public String getRejectedBy()                            { return rejectedBy; }
    public void setRejectedBy(String v)                      { this.rejectedBy = v; }
    public LocalDateTime getRejectedAt()                     { return rejectedAt; }
    public void setRejectedAt(LocalDateTime v)               { this.rejectedAt = v; }
    public String getRejectionReason()                       { return rejectionReason; }
    public void setRejectionReason(String v)                 { this.rejectionReason = v; }
    public String getRtgsMsgId()                             { return rtgsMsgId; }
    public void setRtgsMsgId(String v)                       { this.rtgsMsgId = v; }
    public String getRtgsRequestPayload()                    { return rtgsRequestPayload; }
    public void setRtgsRequestPayload(String v)              { this.rtgsRequestPayload = v; }
    public String getRtgsResponsePayload()                   { return rtgsResponsePayload; }
    public void setRtgsResponsePayload(String v)             { this.rtgsResponsePayload = v; }
    public String getLastError()                             { return lastError; }
    public void setLastError(String v)                       { this.lastError = v; }
    public LocalDateTime getSentAt()                         { return sentAt; }
    public void setSentAt(LocalDateTime v)                   { this.sentAt = v; }
    public LocalDateTime getConfirmedAt()                    { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime v)              { this.confirmedAt = v; }
    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public LocalDateTime getUpdatedAt()                      { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)                { this.updatedAt = v; }
}
