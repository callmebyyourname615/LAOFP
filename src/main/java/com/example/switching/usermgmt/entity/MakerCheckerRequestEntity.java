package com.example.switching.usermgmt.entity;

import java.time.Instant;
import java.util.UUID;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "smos_maker_checker_requests")
public class MakerCheckerRequestEntity {
    @Id private UUID id;
    @Column(name = "request_type", nullable = false, length = 64) private String requestType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb") private JsonNode payloadJson;
    @Column(name = "payload_sha256", nullable = false, length = 64) private String payloadSha256;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "maker_id", nullable = false) private UserEntity maker;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "checker_id") private UserEntity checker;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MakerCheckerStatus status = MakerCheckerStatus.PENDING;
    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false) private Instant submittedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_notes", length = 512) private String decisionNotes;
    @Column(name = "execution_reference", length = 160) private String executionReference;
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String value) { requestType = value; }
    public JsonNode getPayloadJson() { return payloadJson; }
    public void setPayloadJson(JsonNode value) { payloadJson = value; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String value) { payloadSha256 = value; }
    public UserEntity getMaker() { return maker; }
    public void setMaker(UserEntity value) { maker = value; }
    public UserEntity getChecker() { return checker; }
    public void setChecker(UserEntity value) { checker = value; }
    public MakerCheckerStatus getStatus() { return status; }
    public void setStatus(MakerCheckerStatus value) { status = value; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant value) { decidedAt = value; }
    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String value) { decisionNotes = value; }
    public String getExecutionReference() { return executionReference; }
    public void setExecutionReference(String value) { executionReference = value; }
}
