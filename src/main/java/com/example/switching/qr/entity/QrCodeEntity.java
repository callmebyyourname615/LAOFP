package com.example.switching.qr.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "qr_codes")
public class QrCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qr_id", nullable = false, length = 36, updatable = false)
    private String qrId;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "qr_type", nullable = false, length = 10)
    private String qrType;   // STATIC | DYNAMIC

    @Column(name = "payload_text", nullable = false, columnDefinition = "TEXT")
    private String payloadText;

    @Column(name = "amount", precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "txn_ref", length = 100)
    private String txnRef;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── accessors ──────────────────────────────────────────────────────────

    public Long getId()                         { return id; }
    public String getQrId()                     { return qrId; }
    public void   setQrId(String v)             { this.qrId = v; }
    public String getMerchantId()               { return merchantId; }
    public void   setMerchantId(String v)       { this.merchantId = v; }
    public String getPspId()                    { return pspId; }
    public void   setPspId(String v)            { this.pspId = v; }
    public String getQrType()                   { return qrType; }
    public void   setQrType(String v)           { this.qrType = v; }
    public String getPayloadText()              { return payloadText; }
    public void   setPayloadText(String v)      { this.payloadText = v; }
    public BigDecimal getAmount()               { return amount; }
    public void   setAmount(BigDecimal v)       { this.amount = v; }
    public String getCurrency()                 { return currency; }
    public void   setCurrency(String v)         { this.currency = v; }
    public String getTxnRef()                   { return txnRef; }
    public void   setTxnRef(String v)           { this.txnRef = v; }
    public LocalDateTime getExpiresAt()         { return expiresAt; }
    public void   setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
    public boolean isUsed()                     { return used; }
    public void   setUsed(boolean v)            { this.used = v; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
}
