package com.example.switching.rtp.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.switching.rtp.enums.RtpAuthorisationMode;
import com.example.switching.rtp.enums.RtpStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rtp_request")
@Getter
@Setter
@NoArgsConstructor
public class RtpRequestEntity {
    @Id private UUID id;
    @Column(name = "request_correlation_id", nullable = false, length = 64) private String requestCorrelationId;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "payee_participant_id", nullable = false, length = 64) private String payeeParticipantId;
    @Column(name = "payer_participant_id", nullable = false, length = 64) private String payerParticipantId;
    @Column(name = "payee_account", nullable = false, length = 128) private String payeeAccount;
    @Column(name = "payer_account", length = 128) private String payerAccount;
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4) private BigDecimal requestedAmount;
    @Column(name = "authorised_amount", nullable = false, precision = 19, scale = 4) private BigDecimal authorisedAmount;
    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 4) private BigDecimal settledAmount;
    @Column(name = "currency", nullable = false, length = 3) private String currency;
    @Column(name = "description", length = 280) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private RtpStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "authorisation_mode", length = 24) private RtpAuthorisationMode authorisationMode;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "transfer_reference", length = 128) private String transferReference;
    @Column(name = "settlement_reference", length = 128) private String settlementReference;
    @Column(name = "settlement_inquiry_ref", length = 64) private String settlementInquiryRef;
    @Column(name = "decline_reason", length = 500) private String declineReason;
    @Column(name = "declined_at") private Instant declinedAt;
    @Column(name = "authorised_at") private Instant authorisedAt;
    @Column(name = "settled_at") private Instant settledAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}
