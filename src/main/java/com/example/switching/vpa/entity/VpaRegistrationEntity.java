package com.example.switching.vpa.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vpa_registrations")
public class VpaRegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vpa_id", nullable = false, length = 36, updatable = false)
    private String vpaId;

    @Column(name = "vpa_type", nullable = false, length = 20, updatable = false)
    private String vpaType;

    @Column(name = "vpa_value", nullable = false, length = 200, updatable = false)
    private String vpaValue;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "account_ref", nullable = false, length = 200)
    private String accountRef;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType = "BANK_ACCOUNT";

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = true;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()                            { return id; }
    public String getVpaId()                       { return vpaId; }
    public void   setVpaId(String v)               { this.vpaId = v; }
    public String getVpaType()                     { return vpaType; }
    public void   setVpaType(String v)             { this.vpaType = v; }
    public String getVpaValue()                    { return vpaValue; }
    public void   setVpaValue(String v)            { this.vpaValue = v; }
    public String getPspId()                       { return pspId; }
    public void   setPspId(String v)               { this.pspId = v; }
    public String getAccountRef()                  { return accountRef; }
    public void   setAccountRef(String v)          { this.accountRef = v; }
    public String getAccountType()                 { return accountType; }
    public void   setAccountType(String v)         { this.accountType = v; }
    public String getDisplayName()                 { return displayName; }
    public void   setDisplayName(String v)         { this.displayName = v; }
    public boolean isPrimary()                     { return isPrimary; }
    public void   setPrimary(boolean v)            { this.isPrimary = v; }
    public String getStatus()                      { return status; }
    public void   setStatus(String v)              { this.status = v; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public void   setUpdatedAt(LocalDateTime v)    { this.updatedAt = v; }
}
