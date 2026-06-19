package com.example.switching.reconciliation.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line item from a reconciliation file.
 *
 * <p>The underlying table ({@code reconciliation_items}) is partitioned by
 * {@code reconciliation_date}.  Inserts must go through {@link org.springframework.jdbc.core.JdbcTemplate}
 * rather than JPA so that the partition key is included from the start.
 * JPA is fine for reads (PostgreSQL routes queries through the parent table automatically).
 *
 * <p>Match lifecycle: UNMATCHED → MATCHED | DISPUTED
 */
@Entity
@Table(name = "reconciliation_items")
public class ReconciliationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    /** Our internal transaction reference (transfer_ref). May be null if not provided. */
    @Column(name = "transaction_ref")
    private String transactionRef;

    /** The bank's own reference for the transaction. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    /** UNMATCHED | MATCHED | DISPUTED */
    @Column(name = "match_status", nullable = false)
    private String matchStatus = "UNMATCHED";

    @Column(name = "mismatch_reason", columnDefinition = "TEXT")
    private String mismatchReason;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    public String getMismatchReason() { return mismatchReason; }
    public void setMismatchReason(String mismatchReason) { this.mismatchReason = mismatchReason; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public LocalDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(LocalDateTime matchedAt) { this.matchedAt = matchedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
