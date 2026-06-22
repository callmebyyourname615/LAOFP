package com.example.switching.rtp.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.switching.rtp.enums.RtpInstallmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rtp_installment_schedule")
@Getter
@Setter
@NoArgsConstructor
public class RtpInstallmentScheduleEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private RtpInstallmentStatus status;

    @Column(name = "transaction_reference", unique = true, length = 64)
    private String transactionReference;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
