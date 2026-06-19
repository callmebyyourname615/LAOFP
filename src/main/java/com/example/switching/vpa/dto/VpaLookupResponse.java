package com.example.switching.vpa.dto;

import java.time.LocalDateTime;

/**
 * Returned by {@code POST /v1/lookup/resolve}.
 * The caller receives a one-time beneficiaryToken that must be supplied
 * in the subsequent {@code POST /api/transfers} request.
 */
public class VpaLookupResponse {

    private String beneficiaryToken;
    private String displayName;
    private String receivingPspId;
    private String accountType;
    private LocalDateTime expiresAt;

    public VpaLookupResponse() {}

    public VpaLookupResponse(String beneficiaryToken,
                              String displayName,
                              String receivingPspId,
                              String accountType,
                              LocalDateTime expiresAt) {
        this.beneficiaryToken = beneficiaryToken;
        this.displayName      = displayName;
        this.receivingPspId   = receivingPspId;
        this.accountType      = accountType;
        this.expiresAt        = expiresAt;
    }

    public String getBeneficiaryToken()             { return beneficiaryToken; }
    public void   setBeneficiaryToken(String v)     { this.beneficiaryToken = v; }
    public String getDisplayName()                  { return displayName; }
    public void   setDisplayName(String v)          { this.displayName = v; }
    public String getReceivingPspId()               { return receivingPspId; }
    public void   setReceivingPspId(String v)       { this.receivingPspId = v; }
    public String getAccountType()                  { return accountType; }
    public void   setAccountType(String v)          { this.accountType = v; }
    public LocalDateTime getExpiresAt()             { return expiresAt; }
    public void   setExpiresAt(LocalDateTime v)     { this.expiresAt = v; }
}
