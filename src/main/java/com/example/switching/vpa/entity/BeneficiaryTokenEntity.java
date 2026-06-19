package com.example.switching.vpa.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "beneficiary_tokens")
public class BeneficiaryTokenEntity {

    @Id
    @Column(name = "token_id", length = 36)
    private String tokenId;

    @Column(name = "vpa_id", nullable = false, length = 36, updatable = false)
    private String vpaId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public String getTokenId()                    { return tokenId; }
    public void   setTokenId(String v)            { this.tokenId = v; }
    public String getVpaId()                      { return vpaId; }
    public void   setVpaId(String v)              { this.vpaId = v; }
    public LocalDateTime getIssuedAt()            { return issuedAt; }
    public void   setIssuedAt(LocalDateTime v)    { this.issuedAt = v; }
    public LocalDateTime getExpiresAt()           { return expiresAt; }
    public void   setExpiresAt(LocalDateTime v)   { this.expiresAt = v; }
    public boolean isUsed()                       { return used; }
    public void   setUsed(boolean v)              { this.used = v; }
    public LocalDateTime getUsedAt()              { return usedAt; }
    public void   setUsedAt(LocalDateTime v)      { this.usedAt = v; }
}
